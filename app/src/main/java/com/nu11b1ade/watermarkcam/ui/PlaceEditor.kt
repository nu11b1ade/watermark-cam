package com.nu11b1ade.watermarkcam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nu11b1ade.watermarkcam.domain.Coordinates
import com.nu11b1ade.watermarkcam.domain.MAX_WATERMARK_TEXT_LENGTH
import com.nu11b1ade.watermarkcam.domain.OverrideInput
import com.nu11b1ade.watermarkcam.domain.formatCoordinate
import com.nu11b1ade.watermarkcam.domain.parseOverrides
import com.nu11b1ade.watermarkcam.location.ForwardGeocoder
import com.nu11b1ade.watermarkcam.location.LocationReading
import com.nu11b1ade.watermarkcam.location.PlaceCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Shared by camera settings and the history editor, including stale-result/manual-edit protection. */
@Composable
internal fun PlaceEditor(
  enabled: Boolean = true,
  place: String,
  latitude: String,
  longitude: String,
  onPlaceChange: (String) -> Unit,
  onLatitudeChange: (String) -> Unit,
  onLongitudeChange: (String) -> Unit,
  forwardGeocoder: ForwardGeocoder,
  onUseCurrentLocation: suspend () -> LocationReading?,
  showErrors: Boolean,
) {
  var coordsManuallyEdited by rememberSaveable { mutableStateOf(false) }
  var lookup by remember { mutableStateOf(PlaceLookupUiState()) }
  var lookupRequest by remember { mutableStateOf<PlaceLookupRequest?>(null) }
  var lookupSequence by remember { mutableIntStateOf(0) }
  // Read the latest draft even if an async result arrives before recomposition.
  var currentPlace by remember { mutableStateOf(place) }
  currentPlace = place
  val placeError = parseOverrides(OverrideInput(placeEnabled = enabled, place = place)).exceptionOrNull()?.message
  val coordinatesError = if (placeError == null) {
    parseOverrides(OverrideInput(placeEnabled = enabled, place = place, latitude = latitude, longitude = longitude))
      .exceptionOrNull()?.message
  } else null

  fun changePlace(value: String) {
    currentPlace = value
    onPlaceChange(value)
  }
  fun fillCoordinates(coordinates: Coordinates) {
    onLatitudeChange(formatCoordinate(coordinates.latitude))
    onLongitudeChange(formatCoordinate(coordinates.longitude))
  }
  fun applyCandidates(trigger: PlaceLookupTrigger, results: List<PlaceCandidate>) {
    val decision = placeCoordinatesAfterLookup(trigger, coordsManuallyEdited, results)
    decision.coordinatesToFill?.let(::fillCoordinates)
    coordsManuallyEdited = decision.manuallyEdited
  }
  fun isCurrentLookup(request: PlaceLookupRequest): Boolean = enabled && lookupRequest == request && when (request) {
    is PlaceLookupRequest.Search -> request.query == currentPlace
    is PlaceLookupRequest.Resolve -> request.candidate.name == currentPlace
    is PlaceLookupRequest.CurrentLocation -> true
  }

  LaunchedEffect(place, enabled, lookupRequest) {
    if (!enabled) {
      lookupRequest = null
      lookup = PlaceLookupUiState()
      return@LaunchedEffect
    }
    val request = lookupRequest ?: return@LaunchedEffect
    if (!isCurrentLookup(request)) return@LaunchedEffect
    when (request) {
      is PlaceLookupRequest.Search -> {
        if (currentPlace.isBlank()) return@LaunchedEffect
        if (request.trigger == PlaceLookupTrigger.DEBOUNCE) delay(800)
        if (!isCurrentLookup(request)) return@LaunchedEffect
        lookup = PlaceLookupUiState(message = "正在查找…")
        val result = forwardGeocoder.searchResult(request.query)
        currentCoroutineContext().ensureActive()
        if (!isCurrentLookup(request)) return@LaunchedEffect
        val candidates = result.getOrNull().orEmpty().take(5)
        applyCandidates(PlaceLookupTrigger.DEBOUNCE, candidates)
        lookup = PlaceLookupUiState(candidates = candidates, message = when {
          result.isFailure -> "查找失败，请检查网络，或手动填经纬度"
          candidates.isEmpty() -> "没找到这个地点，可以手动填经纬度"
          else -> null
        })
      }
      is PlaceLookupRequest.CurrentLocation -> {
        lookup = PlaceLookupUiState(message = "正在定位…", locating = true)
        val outcome = try {
          val reading = onUseCurrentLocation()
          currentCoroutineContext().ensureActive()
          if (!isCurrentLookup(request)) return@LaunchedEffect
          if (reading?.coordinates == null) PlaceLookupUiState(message = "定位失败，请检查定位设置，或手动填写地点和经纬度") else {
            changePlace(reading.address.take(MAX_WATERMARK_TEXT_LENGTH))
            fillCoordinates(reading.coordinates)
            coordsManuallyEdited = false
            PlaceLookupUiState()
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: SecurityException) {
          PlaceLookupUiState(message = "需要定位权限才能使用当前位置")
        } catch (_: Exception) {
          PlaceLookupUiState(message = "定位失败，请检查定位设置，或手动填写地点和经纬度")
        }
        if (!isCurrentLookup(request)) return@LaunchedEffect
        lookup = outcome
      }
      is PlaceLookupRequest.Resolve -> {
        lookup = PlaceLookupUiState(message = "正在获取经纬度…")
        val resolved = try {
          forwardGeocoder.resolve(request.candidate)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          null
        }
        currentCoroutineContext().ensureActive()
        if (!isCurrentLookup(request)) return@LaunchedEffect
        // A coordinate edit made while resolving must win over the delayed result.
        applyCandidates(PlaceLookupTrigger.DEBOUNCE, listOfNotNull(resolved))
        lookup = PlaceLookupUiState(message = if (resolved?.coordinates == null) "未获取到坐标，可以手动填经纬度" else null)
      }
    }
    if (lookupRequest == request) lookupRequest = null
  }

  if (enabled) {
    OutlinedTextField(
      value = place, onValueChange = {
        val value = it.take(MAX_WATERMARK_TEXT_LENGTH)
        changePlace(value)
        lookup = PlaceLookupUiState()
        lookupRequest = if (value.isBlank()) null else PlaceLookupRequest.Search(value, PlaceLookupTrigger.DEBOUNCE, ++lookupSequence)
      },
      label = { Text("地点 / 地址") }, placeholder = { Text("例如：人民公园南门") },
      isError = showErrors && placeError != null,
      supportingText = if (showErrors && placeError != null) ({ Text(placeError) }) else null,
      modifier = Modifier.fillMaxWidth(), maxLines = 3, shape = RoundedCornerShape(14.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = {
          applyCandidates(PlaceLookupTrigger.BUTTON, emptyList())
          lookup = PlaceLookupUiState()
          lookupRequest = PlaceLookupRequest.Search(currentPlace, PlaceLookupTrigger.BUTTON, ++lookupSequence)
        },
        enabled = place.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
      ) { Text("查找经纬度") }
      OutlinedButton(
        onClick = { lookupRequest = PlaceLookupRequest.CurrentLocation(++lookupSequence) },
        enabled = !lookup.locating, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
      ) { Text("用当前位置") }
    }
    lookup.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (lookup.candidates.isNotEmpty()) {
      Text("候选地点 · 点击选择", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      PlaceCandidates(lookup.candidates) { candidate ->
        applyCandidates(PlaceLookupTrigger.PICK, listOf(candidate))
        changePlace(candidate.name)
        lookup = PlaceLookupUiState()
        lookupRequest = if (candidate.coordinates != null) null else {
          onLatitudeChange("")
          onLongitudeChange("")
          PlaceLookupRequest.Resolve(candidate, ++lookupSequence)
        }
      }
    }
    CoordinateField("纬度", "范围 -90 ～ 90", latitude, { onLatitudeChange(it); coordsManuallyEdited = true }, coordinatesError, ImeAction.Next)
    CoordinateField("经度", "范围 -180 ～ 180", longitude, { onLongitudeChange(it); coordsManuallyEdited = true }, coordinatesError, ImeAction.Done)
    Text("输入地点后可自动填写坐标，也可手动修改。坐标需同时填写或留空；留空时不显示经纬度和自动天气。",
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

private sealed interface PlaceLookupRequest {
  data class Search(val query: String, val trigger: PlaceLookupTrigger, val sequence: Int) : PlaceLookupRequest
  data class Resolve(val candidate: PlaceCandidate, val sequence: Int) : PlaceLookupRequest
  data class CurrentLocation(val sequence: Int) : PlaceLookupRequest
}

private data class PlaceLookupUiState(
  val candidates: List<PlaceCandidate> = emptyList(), val message: String? = null, val locating: Boolean = false,
)

@Composable
private fun PlaceCandidates(candidates: List<PlaceCandidate>, onPick: (PlaceCandidate) -> Unit) {
  Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
    candidates.forEachIndexed { index, candidate ->
      if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onPick(candidate) }).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
        if (candidate.fullName != candidate.name) Text(candidate.fullName,
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(candidate.coordinates?.let { "纬度 ${formatCoordinate(it.latitude)}, 经度 ${formatCoordinate(it.longitude)}" } ?: "点击获取经纬度",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

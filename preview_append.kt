
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun TimelineContainerPreview() {
    TimelineContainer(
        state = TimelineState(currentTimeMs = 1500),
        onEvent = {}
    )
}

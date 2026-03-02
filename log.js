const args = process.argv.splice(2)
const index = Number(args[0])
const outputFile = args[1]

const reset = "adb logcat -c"
const adb = "adb logcat -v raw"

const tags = ["TrimViewModel", "ThumbnailCacheManager",
    "LoadingOverlay",
    "AudioDataExxtractor",
]

const filters  =[
`adb logcat | grep -E "PARALLEL PRELOAD|SnapshotFlow|Cache HIT"`,
    ` | grep -E ${tags.join('|')}`,
    `| grep -E "TrimScreen|LoadingOverlay|PreloadUiState"`,
` TrimScreen:I LoadingOverlay:I PreloadUiState:I '*:S'`,
]

//cache monitor
const cache = `adb logcat -s ThumbnailStrip ThumbnailCacheManager ThumbnailViewModel ThumbnailExtractorBatch`


const command = cache



if (index == 0)
    filters.map((c, i) => console.log((i+1) +':' + c))
else{
    console.log(command)
    return command
}



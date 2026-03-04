const args = process.argv.splice(2)
const index = Number(args[0])
const outputFile = args[1]

const resetLogs = "adb logcat -c && "
const adb = "adb logcat -v"

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


// 1. Extração de thumbs
const extracao = `
adb logcat -c && adb logcat -v time -s ThumbnailStrip:D ThumbnailAspectMonitor:D ThumbnailCacheManager:D ThumbnailViewModel:D | grep -E "(extractSegment|Extracting segment|Batch extraction|extractBatch)"
`// 2. Montagem das strips
const stripss = `
adb logcat -c && adb logcat -v time -s ThumbnailStrip:D ThumbnailAspectMonitor:D | grep -E "(Stitch|drawBitmap|strip|canvas)"
`
// 3. Operações do cache (inserção, consulta, recuperação)
const cach = `
adb logcat -c && adb logcat -v time -s ThumbnailStrip:D ThumbnailCacheManager:D | grep -E "(Cache HIT|Cache MISS|Cached segment|loadFromCache|saveToCache)"
`

const tudoJunto = `
adb logcat -c && adb logcat -v time -s 'ThumbnailStrip:*' 'ThumbnailAspectMonitor:*' 'ThumbnailCacheManager:*' 'ThumbnailViewModel:*' | grep -E "(extractSegment|Extracting segment|Batch extraction|COMPLETED|drawBitmap|Strip|Cache HIT|Cache MISS|Cached segment|loadFromCache|saveToCache|Saving segment)"`

const array  = [
    `adb logcat -c && adb logcat -v brief -s "ThumbnailStrip:*" "ThumbnailAspectMonitor:*" "ThumbnailCacheManager:*" "ThumbnailViewModel:*" | grep -E "(extractSegment|Extracting segment|Batch extraction|COMPLETED|drawBitmap|Strip|totalSegments|segments out of|strips loaded|loaded|Loading)"`,
]

console.log( {extracao, stripss, cach})

import { LogParser, createInitialState } from './src/EntityParser';
import type { AppState, LogEntry } from './src/entities';

async function demoEntityMapping() {
  console.log('═══════════════════════════════════════════════════════════');
  console.log('║              ENTITY MAPPING DEMO                         ║');
  console.log('╚═══════════════════════════════════════════════════════════\n');

  const parser = new LogParser();
  let appState = createInitialState();

  const sampleLogs = [
    'BottomSheetGallery.kt:204:[LOG] Log.d("BottomSheetGallery", "=== VIDEO SELECTED ===")',
    'BottomSheetGallery.kt:205:[LOG] Log.d("BottomSheetGallery", "uri: content://media/video/123")',
    'BottomSheetGallery.kt:206:[LOG] Log.d("BottomSheetGallery", "id: video_001")',
    'TimelineEditor.kt:132:[LOG] Timber.v("Scroll velocity: 150 px/ms")',
    'PreloadViewModel.kt:89:[LOG] Log.d("PreloadViewModel", "=== startPreload CALLED ===")',
    'PreloadViewModel.kt:90:[LOG] Log.d("PreloadViewModel", "uri: content://media/video/123")',
    'PreloadViewModel.kt:91:[LOG] Log.d("PreloadViewModel", "stripsToPreload: 5")',
    'PreloadViewModel.kt:92:[LOG] Log.d("PreloadViewModel", "activeUri: content://media/video/123")',
    'PreloadViewModel.kt:93:[LOG] Log.d("PreloadViewModel", "currentState: PRELOADING")',
    'PreloadViewModel.kt:94:[LOG] Log.d("PreloadViewModel", "strips loaded: 3")',
    'PreloadViewModel.kt:95:[LOG] Log.d("PreloadViewModel", "totalSegments: 10")',
    'PreloadViewModel.kt:96:[LOG] Log.d("PreloadViewModel", "ready=true")',
    'PreloadViewModel.kt:97:[LOG] Log.d("PreloadViewModel", "threshold=2")',
    'TimelineEditor.kt:270:[LOG] Log.e("TimelineEditor", "ExoPlayer error: Invalid state transition", error)',
    '[LOG] Timber.d("GLRenderer initialized successfully")',
    '[LOG] Timber.d("Handles: pos=1, texCoord=2, mvp=3, texMat=4, sampler=5")',
    '[LOG] Timber.d("External texture ID: 12345")',
    '[LOG] Timber.e(e, "Failed to initialize GLRenderer")',
  ];

  console.log('📝 Parsing sample logs...\n');

  for (const log of sampleLogs) {
    const logEntry = parser.parseLog(log);
    const stateUpdate = parser.parseState(log);

    if (stateUpdate) {
      appState = parser.mergeState(appState, stateUpdate);
    }

    if (logEntry) {
      console.log(`[${logEntry.level.padEnd(7)}] ${logEntry.tag || 'N/A'}: ${logEntry.message.substring(0, 60)}`);
    }
  }

  console.log('\n═══════════════════════════════════════════════════════════');
  console.log('║              MAPPED APP STATE                            ║');
  console.log('╚═══════════════════════════════════════════════════════════\n');

  console.log('🎨 GRAPHICS');
  console.log(`  GLRenderer Initialised: ${appState.graphics.glRenderer.isInitialized}`);
  console.log(`  Position Handle: ${appState.graphics.glRenderer.positionHandle ?? 'N/A'}`);
  console.log(`  Texture Coord Handle: ${appState.graphics.glRenderer.textureCoordHandle ?? 'N/A'}`);
  console.log(`  MVP Matrix Handle: ${appState.graphics.glRenderer.mvpMatrixHandle ?? 'N/A'}`);
  console.log(`  Texture Matrix Handle: ${appState.graphics.glRenderer.textureMatrixHandle ?? 'N/A'}`);
  console.log(`  Texture Sampler Handle: ${appState.graphics.glRenderer.textureSamplerHandle ?? 'N/A'}`);
  console.log(`  External Texture ID: ${appState.graphics.glRenderer.externalTextureId ?? 'N/A'}`);
  console.log(`  Last Error: ${appState.graphics.glRenderer.lastError ?? 'None'}`);

  console.log('\n📱 BOTTOM SHEET GALLERY');
  console.log(`  Is Expanded: ${appState.bottomSheetGallery.isExpanded}`);
  console.log(`  Selected Video ID: ${appState.bottomSheetGallery.selectedVideo?.id ?? 'None'}`);
  console.log(`  Selected Video URI: ${appState.bottomSheetGallery.selectedVideo?.uri ?? 'None'}`);

  console.log('\n⏱️ TIMELINE EDITOR');
  console.log(`  Scroll Velocity: ${appState.timelineEditor.scrollVelocity} px/ms`);
  console.log(`  Is Scrolling: ${appState.timelineEditor.isScrolling}`);
  console.log(`  ExoPlayer Error: ${appState.timelineEditor.exoPlayerError ?? 'None'}`);

  console.log('\n🔄 PRELOAD VIEW MODEL');
  console.log(`  Current URI: ${appState.preload.uri ?? 'None'}`);
  console.log(`  Active URI: ${appState.preload.activeUri ?? 'None'}`);
  console.log(`  Strips to Preload: ${appState.preload.stripsToPreload}`);
  console.log(`  Strips Loaded: ${appState.preload.stripsLoaded}`);
  console.log(`  Total Segments: ${appState.preload.totalSegments}`);
  console.log(`  Current State: ${appState.preload.currentState}`);
  console.log(`  Is Ready: ${appState.preload.isReady}`);
  console.log(`  Threshold: ${appState.preload.threshold}`);

  console.log('\n═══════════════════════════════════════════════════════════\n');

  console.log('💾 Saving mapped state to JSON...');
  await Bun.write('app-state.json', JSON.stringify(appState, null, 2));
  console.log('✓ State saved to app-state.json\n');
}

demoEntityMapping().catch(console.error);

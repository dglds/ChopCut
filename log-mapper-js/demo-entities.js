/**
 * @file Demo de mapeamento de entidades
 * @module demo-entities
 * @description Demonstra o parsing de logs e mapeamento para entidades da aplicação
 */

const { LogParser, createInitialState } = require('./EntityParser');

/**
 * Logs de exemplo para demonstração
 * @constant {string[]}
 */
const SAMPLE_LOGS = [
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

/**
 * Imprime o estado do GLRenderer
 * @param {Object} glRenderer - Estado do renderizador OpenGL
 */
function printGraphicsState(glRenderer) {
  console.log('🎨 GRAPHICS');
  console.log(`  GLRenderer Initialised: ${glRenderer.isInitialized}`);
  console.log(`  Position Handle: ${glRenderer.positionHandle ?? 'N/A'}`);
  console.log(`  Texture Coord Handle: ${glRenderer.textureCoordHandle ?? 'N/A'}`);
  console.log(`  MVP Matrix Handle: ${glRenderer.mvpMatrixHandle ?? 'N/A'}`);
  console.log(`  Texture Matrix Handle: ${glRenderer.textureMatrixHandle ?? 'N/A'}`);
  console.log(`  Texture Sampler Handle: ${glRenderer.textureSamplerHandle ?? 'N/A'}`);
  console.log(`  External Texture ID: ${glRenderer.externalTextureId ?? 'N/A'}`);
  console.log(`  Last Error: ${glRenderer.lastError ?? 'None'}`);
}

/**
 * Imprime o estado da BottomSheetGallery
 * @param {Object} gallery - Estado da galeria
 */
function printGalleryState(gallery) {
  console.log('\n📱 BOTTOM SHEET GALLERY');
  console.log(`  Is Expanded: ${gallery.isExpanded}`);
  console.log(`  Selected Video ID: ${gallery.selectedVideo?.id ?? 'None'}`);
  console.log(`  Selected Video URI: ${gallery.selectedVideo?.uri ?? 'None'}`);
}

/**
 * Imprime o estado do TimelineEditor
 * @param {Object} editor - Estado do editor de timeline
 */
function printTimelineEditorState(editor) {
  console.log('\n⏱️ TIMELINE EDITOR');
  console.log(`  Scroll Velocity: ${editor.scrollVelocity} px/ms`);
  console.log(`  Is Scrolling: ${editor.isScrolling}`);
  console.log(`  ExoPlayer Error: ${editor.exoPlayerError ?? 'None'}`);
}

/**
 * Imprime o estado do PreloadViewModel
 * @param {Object} preload - Estado de pré-carregamento
 */
function printPreloadState(preload) {
  console.log('\n🔄 PRELOAD VIEW MODEL');
  console.log(`  Current URI: ${preload.uri ?? 'None'}`);
  console.log(`  Active URI: ${preload.activeUri ?? 'None'}`);
  console.log(`  Strips to Preload: ${preload.stripsToPreload}`);
  console.log(`  Strips Loaded: ${preload.stripsLoaded}`);
  console.log(`  Total Segments: ${preload.totalSegments}`);
  console.log(`  Current State: ${preload.currentState}`);
  console.log(`  Is Ready: ${preload.isReady}`);
  console.log(`  Threshold: ${preload.threshold}`);
}

/**
 * Função principal de demonstração
 * @async
 * @description Demonstra o parsing de logs e mapeamento para entidades
 * @returns {Promise<void>}
 */
async function demoEntityMapping() {
  console.log('═══════════════════════════════════════════════════════════');
  console.log('║              ENTITY MAPPING DEMO                         ║');
  console.log('╚═══════════════════════════════════════════════════════════\n');

  const parser = new LogParser();
  let appState = createInitialState();

  console.log('📝 Parsing sample logs...\n');

  /**
   * Parseia cada log de exemplo
   */
  for (const log of SAMPLE_LOGS) {
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

  printGraphicsState(appState.graphics.glRenderer);
  printGalleryState(appState.bottomSheetGallery);
  printTimelineEditorState(appState.timelineEditor);
  printPreloadState(appState.preload);

  console.log('\n═══════════════════════════════════════════════════════════\n');

  console.log('💾 Saving mapped state to JSON...');
  
  /**
   * Salva o estado em arquivo JSON
   */
  const fs = require('fs');
  await fs.promises.writeFile(
    'app-state.json',
    JSON.stringify(appState, null, 2),
    'utf-8'
  );
  
  console.log('✓ State saved to app-state.json\n');
}

/**
 * Executa a demo e captura erros
 */
demoEntityMapping().catch(console.error);

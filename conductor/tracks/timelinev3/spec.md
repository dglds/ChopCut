# Timeline v3 Specification - Minimal Viable Product (MVP)

## Objective
To develop a *minimally viable* Timeline v3 component focused on maximum performance for video playback and horizontal thumbnail scrolling.

## Key Features
-   **Simplified UI:** Consists only of a video player and a horizontal scrollable strip of thumbnails.
-   **Dedicated v3 Extraction/Decoding:** New, optimized methods for extracting and decoding video frames/thumbnails.
-   **MAX_PERFORMANCE Configuration:** An explicit setting to prioritize maximum performance, optimizing for speed and resource efficiency.

## Scope
-   Implement the simplified UI: video player and horizontal thumbnail scroll.
-   Develop and integrate new v3 extraction and decoding methods.
-   Implement the `MAX_PERFORMANCE` configuration.
-   Basic integration with a video source.

## Non-Goals
-   Any complex UI/UX features beyond the video player and horizontal thumbnail scroll.
-   Advanced editing functionalities.
-   Full backward compatibility with existing timeline components (unless absolutely necessary for core function).
-   Extensive configurability outside of `MAX_PERFORMANCE`.

## Technical Considerations
-   Prioritize performance over visual bells and whistles.
-   Utilize native or highly optimized libraries for video playback, extraction, and decoding.
-   Minimalist architecture to support the MVP.

## Success Metrics
-   Extremely smooth video playback and thumbnail scrolling at `MAX_PERFORMANCE`.
-   Significantly reduced resource consumption (CPU, memory) compared to previous versions.
-   Functional new v3 extraction/decoding methods.


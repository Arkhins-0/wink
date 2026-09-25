"use client";

import Lightbox from "yet-another-react-lightbox";
import Download from "yet-another-react-lightbox/plugins/download";
import Zoom from "yet-another-react-lightbox/plugins/zoom";
import "yet-another-react-lightbox/styles.css";

export type ViewerImage = { id: string; name: string };

/**
 * Photos full screen, as a phone's gallery does it (yet-another-react-lightbox): pinch, the mouse wheel or a double
 * tap zooms in where it points, a zoomed photo pans only to its edges, swipe or the arrows go to the next photo of the
 * same message, and Download saves the one on screen. Esc, the ✕ or a swipe down closes it.
 */
export function ImageViewer({ images, index, onClose }: { images: ViewerImage[]; index: number; onClose: () => void }) {
  return (
    <Lightbox
      open
      close={onClose}
      index={index}
      slides={images.map((f) => ({
        src: `/api/files/${f.id}/content?inline=1`,
        alt: f.name,
        download: { url: `/api/files/${f.id}?go=download`, filename: f.name },
      }))}
      plugins={[Zoom, Download]}
      zoom={{ maxZoomPixelRatio: 4, scrollToZoom: true, doubleClickMaxStops: 2 }}
      controller={{ closeOnPullDown: true, closeOnBackdropClick: true }}
      carousel={{ finite: images.length <= 1 }}
      // One photo: no arrows to nowhere.
      render={images.length <= 1 ? { buttonPrev: () => null, buttonNext: () => null } : undefined}
      styles={{ container: { backgroundColor: "#000" } }}
    />
  );
}

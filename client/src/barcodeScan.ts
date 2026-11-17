// Camera barcode scanning through the BarcodeDetector API where the browser has it.
interface DetectorLike {
  detect(source: CanvasImageSource): Promise<{ rawValue: string }[]>;
}
type DetectorCtor = new (opts?: { formats?: string[] }) => DetectorLike;

export function barcodeScanSupported(): boolean {
  return typeof window !== "undefined" && "BarcodeDetector" in window && !!navigator.mediaDevices?.getUserMedia;
}

/** Scans the video stream until a code is found, the signal aborts, or the camera fails. */
export async function scanBarcode(video: HTMLVideoElement, signal: AbortSignal): Promise<string | null> {
  if (!barcodeScanSupported()) return null;
  const Ctor = (window as unknown as { BarcodeDetector: DetectorCtor }).BarcodeDetector;
  const detector = new Ctor({ formats: ["ean_13", "ean_8", "upc_a", "upc_e"] });
  const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
  try {
    video.srcObject = stream;
    await video.play();
    while (!signal.aborted) {
      const found = await detector.detect(video).catch(() => []);
      if (found.length > 0) return found[0].rawValue;
      await new Promise((r) => setTimeout(r, 300));
    }
    return null;
  } finally {
    stream.getTracks().forEach((t) => t.stop());
    video.srcObject = null;
  }
}

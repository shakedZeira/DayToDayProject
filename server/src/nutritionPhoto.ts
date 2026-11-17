import { Router } from "express";
import multer from "multer";
import { requireAuth } from "./session";
import { createVisionProvider } from "./providers/vision";
import { VisionError } from "./providers/visionGemini";

export const nutritionPhotoRouter = Router();
nutritionPhotoRouter.use(requireAuth);
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 8 * 1024 * 1024 } });
nutritionPhotoRouter.post("/analyze-photo", upload.single("image"), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: "image file required (field 'image')" });
  const provider = await createVisionProvider();
  try {
    res.json(await provider.estimateCalories(req.file.buffer, req.file.originalname));
  } catch (e) {
    if (e instanceof VisionError) return res.status(e.status).json({ error: e.message });
    throw e;
  }
});
export { upload };

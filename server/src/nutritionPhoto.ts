import { Router } from "express";
import multer from "multer";
import { requireAuth } from "./session";
import { createVisionProvider } from "./providers/vision";

export const nutritionPhotoRouter = Router();
nutritionPhotoRouter.use(requireAuth);
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 8 * 1024 * 1024 } });
nutritionPhotoRouter.post("/analyze-photo", upload.single("image"), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: "image file required (field 'image')" });
  const provider = await createVisionProvider();
  const estimate = await provider.estimateCalories(req.file.buffer, req.file.originalname);
  res.json(estimate);
});
export { upload };

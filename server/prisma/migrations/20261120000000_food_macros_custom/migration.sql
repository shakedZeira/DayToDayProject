-- AlterTable
ALTER TABLE "Food" ADD COLUMN     "barcode" TEXT,
ADD COLUMN     "carbsPer100" DOUBLE PRECISION NOT NULL DEFAULT 0,
ADD COLUMN     "fatPer100" DOUBLE PRECISION NOT NULL DEFAULT 0,
ADD COLUMN     "ownerId" TEXT,
ADD COLUMN     "proteinPer100" DOUBLE PRECISION NOT NULL DEFAULT 0;

-- CreateIndex
CREATE INDEX "Food_ownerId_idx" ON "Food"("ownerId");

-- CreateIndex
CREATE INDEX "Food_ownerId_barcode_idx" ON "Food"("ownerId", "barcode");

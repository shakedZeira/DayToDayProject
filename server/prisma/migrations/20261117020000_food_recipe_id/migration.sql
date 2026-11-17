-- AlterTable
ALTER TABLE "Food" ADD COLUMN "recipeId" TEXT;

-- CreateIndex
CREATE INDEX "Food_recipeId_idx" ON "Food"("recipeId");

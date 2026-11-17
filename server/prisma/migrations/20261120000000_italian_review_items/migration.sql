-- CreateTable
CREATE TABLE "ItalianReviewItem" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "lessonId" TEXT NOT NULL,
    "ease" DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    "intervalDays" INTEGER NOT NULL DEFAULT 0,
    "repetitions" INTEGER NOT NULL DEFAULT 0,
    "dueAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastReviewedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ItalianReviewItem_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "ItalianReviewItem_ownerId_dueAt_idx" ON "ItalianReviewItem"("ownerId", "dueAt");

-- CreateIndex
CREATE UNIQUE INDEX "ItalianReviewItem_ownerId_exerciseId_key" ON "ItalianReviewItem"("ownerId", "exerciseId");

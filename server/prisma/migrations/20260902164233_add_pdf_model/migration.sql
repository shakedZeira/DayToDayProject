-- CreateTable
CREATE TABLE "Pdf" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "fileName" TEXT NOT NULL,
    "path" TEXT NOT NULL,
    "size" INTEGER NOT NULL,
    "pageCount" INTEGER NOT NULL DEFAULT 0,
    "lastPage" INTEGER NOT NULL DEFAULT 0,
    "readProgress" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Pdf_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Pdf_ownerId_idx" ON "Pdf"("ownerId");

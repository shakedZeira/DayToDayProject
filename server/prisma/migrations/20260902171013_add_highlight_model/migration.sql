-- CreateTable
CREATE TABLE "Highlight" (
    "id" TEXT NOT NULL,
    "pdfId" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "pageNumber" INTEGER NOT NULL,
    "text" TEXT NOT NULL,
    "note" TEXT,
    "color" TEXT NOT NULL DEFAULT 'yellow',
    "positions" TEXT NOT NULL DEFAULT '[]',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Highlight_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Highlight_pdfId_idx" ON "Highlight"("pdfId");

-- CreateIndex
CREATE INDEX "Highlight_ownerId_idx" ON "Highlight"("ownerId");

-- AddForeignKey
ALTER TABLE "Highlight" ADD CONSTRAINT "Highlight_pdfId_fkey" FOREIGN KEY ("pdfId") REFERENCES "Pdf"("id") ON DELETE CASCADE ON UPDATE CASCADE;

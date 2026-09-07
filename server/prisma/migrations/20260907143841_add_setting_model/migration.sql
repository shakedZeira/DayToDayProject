-- CreateTable
CREATE TABLE "Setting" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "key" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Setting_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Setting_ownerId_idx" ON "Setting"("ownerId");

-- CreateIndex
CREATE UNIQUE INDEX "Setting_ownerId_key_key" ON "Setting"("ownerId", "key");

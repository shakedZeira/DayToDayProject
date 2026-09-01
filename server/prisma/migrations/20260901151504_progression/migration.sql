-- CreateTable
CREATE TABLE "ExerciseProgression" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "exerciseId" TEXT,
    "exerciseName" TEXT NOT NULL,
    "targetSets" INTEGER NOT NULL DEFAULT 3,
    "targetReps" INTEGER NOT NULL DEFAULT 8,
    "targetWeight" DOUBLE PRECISION,
    "incrementKg" DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    "lastProgressed" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ExerciseProgression_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PersonalRecord" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "exerciseId" TEXT,
    "exerciseName" TEXT NOT NULL,
    "repRange" INTEGER NOT NULL,
    "weight" DOUBLE PRECISION NOT NULL,
    "date" TIMESTAMP(3) NOT NULL,
    "e1rm" DOUBLE PRECISION NOT NULL,

    CONSTRAINT "PersonalRecord_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "ExerciseProgression_ownerId_idx" ON "ExerciseProgression"("ownerId");

-- CreateIndex
CREATE UNIQUE INDEX "ExerciseProgression_ownerId_exerciseName_key" ON "ExerciseProgression"("ownerId", "exerciseName");

-- CreateIndex
CREATE INDEX "PersonalRecord_ownerId_idx" ON "PersonalRecord"("ownerId");

-- CreateIndex
CREATE UNIQUE INDEX "PersonalRecord_ownerId_exerciseName_repRange_key" ON "PersonalRecord"("ownerId", "exerciseName", "repRange");

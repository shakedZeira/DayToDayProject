-- CreateTable
CREATE TABLE "ItalianLessonCompletion" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "lessonId" TEXT NOT NULL,
    "xpAwarded" INTEGER NOT NULL DEFAULT 0,
    "completedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ItalianLessonCompletion_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ItalianExerciseAttempt" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "lessonId" TEXT NOT NULL,
    "exerciseId" TEXT NOT NULL,
    "isCorrect" BOOLEAN NOT NULL,
    "userAnswer" TEXT NOT NULL,
    "xpAwarded" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ItalianExerciseAttempt_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "ItalianLessonCompletion_ownerId_idx" ON "ItalianLessonCompletion"("ownerId");

-- CreateIndex
CREATE UNIQUE INDEX "ItalianLessonCompletion_ownerId_lessonId_key" ON "ItalianLessonCompletion"("ownerId", "lessonId");

-- CreateIndex
CREATE INDEX "ItalianExerciseAttempt_ownerId_createdAt_idx" ON "ItalianExerciseAttempt"("ownerId", "createdAt");

-- CreateIndex
CREATE INDEX "ItalianExerciseAttempt_ownerId_exerciseId_idx" ON "ItalianExerciseAttempt"("ownerId", "exerciseId");

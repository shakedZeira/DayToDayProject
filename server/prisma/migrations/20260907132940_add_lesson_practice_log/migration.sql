-- CreateTable
CREATE TABLE "Lesson" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "date" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "tip" TEXT NOT NULL,
    "vocab" TEXT NOT NULL,
    "phrases" TEXT NOT NULL,
    "completedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Lesson_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PracticeLog" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "lessonDate" TEXT NOT NULL,
    "phraseIndex" INTEGER NOT NULL,
    "isCorrect" BOOLEAN NOT NULL,
    "userAttempt" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PracticeLog_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Lesson_ownerId_idx" ON "Lesson"("ownerId");

-- CreateIndex
CREATE UNIQUE INDEX "Lesson_ownerId_date_key" ON "Lesson"("ownerId", "date");

-- CreateIndex
CREATE INDEX "PracticeLog_ownerId_lessonDate_idx" ON "PracticeLog"("ownerId", "lessonDate");

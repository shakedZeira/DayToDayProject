-- AlterTable
ALTER TABLE "Task" ADD COLUMN     "remindAt" TIMESTAMP(3),
ADD COLUMN     "reminderSentAt" TIMESTAMP(3),
ADD COLUMN     "snoozedUntil" TIMESTAMP(3);


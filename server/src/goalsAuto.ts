import { prisma } from "./db";

export async function emitGoalSignals(ownerId: string, signals: Record<string, number>): Promise<void> {
  const sources = Object.entries(signals).filter(([, count]) => count > 0);
  if (sources.length === 0) return;
  const goals = await prisma.weeklyGoal.findMany({
    where: { ownerId, autoSource: { in: sources.map(([s]) => s) } }
  });
  for (const goal of goals) {
    const count = signals[goal.autoSource!];
    if (!count || count <= 0) continue;
    await prisma.goalEvent.create({
      data: { ownerId, goalId: goal.id, date: new Date(), count, source: goal.autoSource! }
    });
  }
}

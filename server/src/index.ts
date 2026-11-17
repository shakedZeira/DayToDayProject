import "dotenv/config";
import { app } from "./app";
import { startScheduler } from "./scheduler";
import { initSentry, installProcessHandlers, logJson } from "./observability";

const port = Number(process.env.PORT) || 4000;

if (require.main === module) {
  initSentry();
  installProcessHandlers();
  startScheduler();
  app.listen(port, () => {
    logJson("info", "server listening", { port });
  });
}

export { app };

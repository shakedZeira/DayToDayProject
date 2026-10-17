import "dotenv/config";
import { app } from "./app";
import { startScheduler } from "./scheduler";

const port = Number(process.env.PORT) || 4000;

if (require.main === module) {
  startScheduler();
  app.listen(port, () => {
    console.log(`Server listening on http://localhost:${port}`);
  });
}

export { app };

import path from "path";
import dotenv from "dotenv";
import { PrismaClient } from "@prisma/client";

dotenv.config({ path: path.join(__dirname, "..", "..", ".env") });

export const prisma = new PrismaClient();
import { prisma } from "./db";
import type { RecipeCreateInput } from "shared";

export type RecipeMeal = "breakfast" | "lunch" | "dinner" | "snack";
export type RecipeDifficulty = "beginner" | "intermediate" | "advanced";

export interface SeedRecipe extends Omit<RecipeCreateInput, "ingredients" | "instructions"> {
  ingredients: string; // JSON string stored in the DB
  instructions: string; // JSON string stored in the DB
}

export const SEED_RECIPES: SeedRecipe[] = [
  {
    title: "פנקייק חלבון",
    description:
      "פנקייקים אווריריים ועתירי חלבון העשויים משיבולת שועל, חלבוני ביצה וקוטג'. ארוחת בוקר מושלמת לאחר אימון.",
    meal: "breakfast",
    difficulty: "beginner",
    prepTimeMin: 5,
    cookTimeMin: 10,
    servings: 2,
    calories: 320,
    proteinG: 32,
    carbsG: 38,
    fatG: 8,
    ingredients: JSON.stringify([
      { name: "שיבולת שועל דקה", amount: 1, unit: "כוס" },
      { name: "גבינת קוטג'", amount: 120, unit: "גרם" },
      { name: "חלבוני ביצה", amount: 4, unit: "גדולים" },
      { name: "ביצה שלמה", amount: 1, unit: "גדולה" },
      { name: "אבקת אפייה", amount: 1, unit: "כפית" },
      { name: "קינמון", amount: 0.5, unit: "כפית" },
      { name: "תמצית וניל", amount: 0.25, unit: "כפית" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "טחן את שיבולת השועל לקמח דק במעבד מזון או בלנדר.", timerSeconds: 30 },
      { step: 2, text: "הוסף קוטג', חלבוני ביצה, ביצה שלמה, אבקת אפייה, קינמון ווניל. ערבב עד לקבלת תערובת חלקה.", timerSeconds: 60 },
      { step: 3, text: "חמם מחבת טפלון על אש בינונית ושמן קלות.", timerSeconds: 0 },
      { step: 4, text: "מזוג כרבע כוס בלילה לכל פנקייק. טגן עד להיווצרות בועות, הפוך ובשל שתי דקות נוספות.", timerSeconds: 180 },
      { step: 5, text: "הגש עם פירות יער טריים ומזיפה של מייפל אם רצוי.", timerSeconds: 0 },
    ]),
    imageUrl: "https://images.unsplash.com/photo-1567620905732-2d1ec7ab7445?w=800&q=80",
    source: "קטלוג מתחילים של DayToDay",
    tags: "ארוחת בוקר,עתיר חלבון,פנקייקים,אחרי אימון",
  },
  {
    title: "קערת עוף ויוגווני יווני",
    description:
      "עוף בגריל על קינואה ועלים עם רוטב יוגווני יווני קרמי. עתיר חלבון, מקרונים מאוזנים.",
    meal: "lunch",
    difficulty: "beginner",
    prepTimeMin: 10,
    cookTimeMin: 15,
    servings: 1,
    calories: 480,
    proteinG: 42,
    carbsG: 40,
    fatG: 14,
    ingredients: JSON.stringify([
      { name: "חזה עוף", amount: 150, unit: "גרם" },
      { name: "קינואה (מבושלת)", amount: 120, unit: "גרם" },
      { name: "יוגווני יווני (רגיל)", amount: 60, unit: "גרם" },
      { name: "תינוק תרד", amount: 40, unit: "גרם" },
      { name: "מלפפון", amount: 60, unit: "גרם" },
      { name: "עגבניות שרי", amount: 60, unit: "גרם" },
      { name: "מיץ לימון", amount: 1, unit: "כף" },
      { name: "שמן זית", amount: 1, unit: "כף" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "תבל חזה עוף במלח, פלפל ופפריקה. צלה או טגן במחבת 6-7 דקות מכל צד עד שהעוף מוכן.", timerSeconds: 600 },
      { step: 2, text: "ערבל יוגווני יווני עם מיץ לימון, מלח ועשבי תיבול לרוטב.", timerSeconds: 60 },
      { step: 3, text: "הרכב קערה: בסיס קינואה, מעל תרד, מלפפון, עגבניות ועוף פרוס.", timerSeconds: 120 },
      { step: 4, text: "מזוג רוטב יוגווני מעל הקערה והגש.", timerSeconds: 30 },
    ]),
    imageUrl: "https://live.staticflickr.com/3595/5757848105_44e2a0b681_b.jpg",
    source: "קטלוג מתחילים של DayToDay",
    tags: "ארוחת צהריים,עתיר חלבון,עוף,הכנה מראש",
  },
  {
    title: "קציצות הודו במרינרה",
    description:
      "קציצות הודו רזות ברוטב מרינרה על ספגטי מחיטה מלאה. ארוחת ערב מנחמת ועתירת חלבון.",
    meal: "dinner",
    difficulty: "intermediate",
    prepTimeMin: 15,
    cookTimeMin: 25,
    servings: 2,
    calories: 410,
    proteinG: 36,
    carbsG: 42,
    fatG: 12,
    ingredients: JSON.stringify([
      { name: "הודו טחון (רזה)", amount: 300, unit: "גרם" },
      { name: "ספגטי מחיטה מלאה", amount: 180, unit: "גרם" },
      { name: "רוטב מרינרה", amount: 300, unit: "גרם" },
      { name: "גבינת פרמזן", amount: 30, unit: "גרם" },
      { name: "ביצה", amount: 1, unit: "גדולה" },
      { name: "פירורי לחם", amount: 30, unit: "גרם" },
      { name: "שום", amount: 2, unit: "שן" },
      { name: "בזיליקום טרי", amount: 10, unit: "גרם" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "ערבב הודו טחון עם ביצה, פירורי לחם, שום קצוץ, מלח ופלפל. צור 12 קציצות.", timerSeconds: 180 },
      { step: 2, text: "השחים קציצות במחבת על אש בינונית, הוסף מרינרה ובשל על אש קטנה 15 דקות.", timerSeconds: 900 },
      { step: 3, text: "בשל ספגטי לפי הוראות האריזה, סנן.", timerSeconds: 600 },
      { step: 4, text: "הגש קציצות ורוטב על הפסטה, מעל פרמזן ובזיליקום.", timerSeconds: 120 },
    ]),
    imageUrl: "https://images.unsplash.com/photo-1529042410759-befb1204b468?w=800&q=80",
    source: "קטלוג מתחילים של DayToDay",
    tags: "ארוחת ערב,עתיר חלבון,הודו,פסטה,אוכל מנחם",
  },
  {
    title: "קערת סלמון וקינואה",
    description:
      "סלמון עשיר באומגה-3 עם קינואה, ירקות צלויים ואבוקדו. ארוחת ערב מזינה להתאוששות.",
    meal: "dinner",
    difficulty: "beginner",
    prepTimeMin: 10,
    cookTimeMin: 20,
    servings: 1,
    calories: 520,
    proteinG: 38,
    carbsG: 45,
    fatG: 20,
    ingredients: JSON.stringify([
      { name: "פילה סלמון", amount: 150, unit: "גרם" },
      { name: "קינואה (מבושלת)", amount: 100, unit: "גרם" },
      { name: "ברוקולי", amount: 100, unit: "גרם" },
      { name: "אבוקדו", amount: 60, unit: "גרם" },
      { name: "לימון", amount: 0.5, unit: "יחידה" },
      { name: "שמן זית", amount: 1, unit: "כף" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "תبل סלמון במלח ופלפל. אפה ב-200 מעלות צלזיוס 12-15 דקות.", timerSeconds: 900 },
      { step: 2, text: "אדה או צלה ברוקולי עד שיהיה רך-פריך.", timerSeconds: 600 },
      { step: 3, text: "פרוס אבוקדו. מפרק סלמון מבושל במזלג.", timerSeconds: 180 },
      { step: 4, text: "הרכב קערה עם קינואה, ברוקולי, סלמון ואבוקדו. סחט לימון מעל.", timerSeconds: 120 },
    ]),
    imageUrl: "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=800&q=80",
    source: "קטלוג מתחילים של DayToDay",
    tags: "ארוחת ערב,עתיר חלבון,סלמון,אומגה-3",
  },
  {
    title: "אומלט חלבוני ביצה וירקות",
    description:
      "אומלט דל קלוריות ועתיר חלבוןמלא בירקות. ארוחת בוקר מהירה להתחלת היום.",
    meal: "breakfast",
    difficulty: "beginner",
    prepTimeMin: 5,
    cookTimeMin: 8,
    servings: 1,
    calories: 220,
    proteinG: 28,
    carbsG: 12,
    fatG: 8,
    ingredients: JSON.stringify([
      { name: "חלבוני ביצה", amount: 6, unit: "גדולים" },
      { name: "ביצה שלמה", amount: 1, unit: "גדולה" },
      { name: "תינוק תרד", amount: 30, unit: "גרם" },
      { name: "פלפל", amount: 50, unit: "גרם" },
      { name: "בצל", amount: 30, unit: "גרם" },
      { name: "גבינת פטה", amount: 30, unit: "גרם" },
      { name: "תרסיס שמן זית", amount: 1, unit: "התזה" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "הקצף חלבוני ביצה וביצה שלמה בקערה עם מלח ופלפל.", timerSeconds: 60 },
      { step: 2, text: "טגן תרד, פלפל ובצל במחבת טפלון עד לריכוך.", timerSeconds: 180 },
      { step: 3, text: "מזוג ביצים, מפזר פטה, ומבשל עד להתייצבות. מקפל ומגיש.", timerSeconds: 300 },
    ]),
    imageUrl: "https://live.staticflickr.com/7302/9742060066_2216312d52_b.jpg",
    source: "קטלוג מתחילים של DayToDay",
    tags: "ארוחת בוקר,עתיר חלבון,ביצים,צמחוני,מהיר",
  },
  {
    title: "סלט טונה וחומוס",
    description:
      "סלט ללא בישול המשלב טונה, חומוס וירקות ים תיכוניים עם רוטב לימון ושמן זית.",
    meal: "lunch",
    difficulty: "beginner",
    prepTimeMin: 10,
    cookTimeMin: 0,
    servings: 1,
    calories: 430,
    proteinG: 35,
    carbsG: 42,
    fatG: 16,
    ingredients: JSON.stringify([
      { name: "טונה משומרת (במים)", amount: 120, unit: "גרם" },
      { name: "חומוס (משומר, שטוף)", amount: 100, unit: "גרם" },
      { name: "מלפפון", amount: 60, unit: "גרם" },
      { name: "עגבניות שרי", amount: 60, unit: "גרם" },
      { name: "בצל אדום", amount: 30, unit: "גרם" },
      { name: "זיתים", amount: 30, unit: "גרם" },
      { name: "שמן זית", amount: 1, unit: "כף" },
      { name: "מיץ לימון", amount: 1, unit: "כף" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "סנן טונה וחומוס. קצוץ מלפפון, עגבניות, בצל אדום וזיתים.", timerSeconds: 300 },
      { step: 2, text: "ערבל את כל המרכיבים בקערה. מזוג שמן זית ומיץ לימון.", timerSeconds: 120 },
      { step: 3, text: "ערבב בעדינות, תבל במלח ופלפל, והגש קר.", timerSeconds: 60 },
    ]),
    imageUrl: "https://live.staticflickr.com/8478/8284791250_8d422aa77e_b.jpg",
    source: "קטלוג מתחילים של DayToDay",
    tags: "ארוחת צהריים,עתיר חלבון,טונה,ללא בישול,ים תיכוני",
  },
  {
    title: "רצועות בקר וברוקולי מוקפץ",
    description:
      "פרטי בקר רכים עם ברוקולי ברוטב מלוח מעל אורז חום. ארוחת ערב קלאסית ועתירת חלבון.",
    meal: "dinner",
    difficulty: "intermediate",
    prepTimeMin: 10,
    cookTimeMin: 15,
    servings: 2,
    calories: 440,
    proteinG: 34,
    carbsG: 46,
    fatG: 14,
    ingredients: JSON.stringify([
      { name: "פרטי בקר רזים", amount: 250, unit: "גרם" },
      { name: "ברוקולי", amount: 200, unit: "גרם" },
      { name: "אורז חום (מבושל)", amount: 200, unit: "גרם" },
      { name: "רוטב סויה", amount: 2, unit: "כף" },
      { name: "שום", amount: 2, unit: "שן" },
      { name: "ג'ינג'ר", amount: 10, unit: "גרם" },
      { name: "שמן שומשום", amount: 1, unit: "כף" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "פורס בקר לרצועות דקות. משרה עם כף רוטב סויה, שום קצוץ וג'ינג'ר.", timerSeconds: 300 },
      { step: 2, text: "מחמם שמן שומשום בווק או מחבת על אש גבוהה. מקפיץ בקר עד להשחמה, מוציא.", timerSeconds: 300 },
      { step: 3, text: "מקפיץ ברוקולי עד שיהיה ירוק בהיר ורך-פריך.", timerSeconds: 300 },
      { step: 4, text: "מחזיר בקר למחבת, מוסיף את שאר רוטב הסויה, מערבב לציפוי. מגיש מעל אורז חום.", timerSeconds: 300 },
    ]),
    imageUrl: "https://live.staticflickr.com/7287/8742005207_5b63a1d6bc.jpg",
    source: "קטלוג מתחילים של DayToDay",
    tags: "ארוחת ערב,עתיר חלבון,בקר,מוקפץ,אסייתי",
  },
  {
    title: "פרפה קוטג' ופירות יער",
    description:
      "קוטג' בשכבות עם פירות יער טריים, דבש ואגוזים פריכים. חטיף מתוק ועתיר חלבון.",
    meal: "snack",
    difficulty: "beginner",
    prepTimeMin: 5,
    cookTimeMin: 0,
    servings: 1,
    calories: 250,
    proteinG: 24,
    carbsG: 26,
    fatG: 7,
    ingredients: JSON.stringify([
      { name: "קוטג' (5%)", amount: 150, unit: "גרם" },
      { name: "פירות יער מעורבים", amount: 80, unit: "גרם" },
      { name: "דבש", amount: 1, unit: "כף" },
      { name: "שקדים", amount: 10, unit: "גרם" },
      { name: "קינמון", amount: 0.25, unit: "כפית" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "מזג קוטג' לקערה או לכוס.", timerSeconds: 30 },
      { step: 2, text: "מכסה בפירות יער מעורבים, מזלף דבש, ומפזר שקדים וקינמון.", timerSeconds: 120 },
      { step: 3, text: "מגיש מיד כחטיף או ארוחת בוקר קלה.", timerSeconds: 0 },
    ]),
    imageUrl: "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=800&q=80",
    source: "קטלוג מתחילים של DayToDay",
    tags: "חטיף,עתיר חלבון,פירות יער,אגוזים,מתוק",
  },
  {
    title: "שיי חלבון בננה ושיבולת שועל",
    description:
      "שיי מהיר עם בננה, שיבולת שועל ואבקת חלבון. חטיף אידיאלי לפני או אחרי אימון.",
    meal: "snack",
    difficulty: "beginner",
    prepTimeMin: 5,
    cookTimeMin: 0,
    servings: 1,
    calories: 380,
    proteinG: 30,
    carbsG: 48,
    fatG: 8,
    ingredients: JSON.stringify([
      { name: "בננה", amount: 1, unit: "יחידה" },
      { name: "שיבולת שועל", amount: 40, unit: "גרם" },
      { name: "אבקת חלבון מי גבינה", amount: 30, unit: "גרם" },
      { name: "חלב (מלא)", amount: 250, unit: "מיליליטר" },
      { name: "חמאת בוטנים", amount: 10, unit: "גרם" },
      { name: "קוביות קרח", amount: 5, unit: "יחידות" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "הוסף את כל המרכיבים לבלנדר. ערבב עד לקבלת מרקם חלקה וקרמי.", timerSeconds: 60 },
      { step: 2, text: "מזוג לכוס והיהנה מיד.", timerSeconds: 30 },
    ]),
    imageUrl: "https://live.staticflickr.com/5012/5484701810_3a5e2a5aff_b.jpg",
    source: "קטלוג מתחילים של DayToDay",
    tags: "חטיף,עתיר חלבון,שיי,לפני אימון",
  },
  {
    title: "האש סטייק ובטטה",
    description:
      "פרטי סטייק עסיסיים עם בטטה קצוצה, פלפלים ובצל. ארוחת בוקר או צהריים מאוחרת דשנה ועתירת חלבון.",
    meal: "breakfast",
    difficulty: "intermediate",
    prepTimeMin: 10,
    cookTimeMin: 20,
    servings: 1,
    calories: 560,
    proteinG: 38,
    carbsG: 44,
    fatG: 18,
    ingredients: JSON.stringify([
      { name: "סטייק (סינטה)", amount: 150, unit: "גרם" },
      { name: "בטטה", amount: 150, unit: "גרם" },
      { name: "פלפל", amount: 60, unit: "גרם" },
      { name: "בצל", amount: 40, unit: "גרם" },
      { name: "ביצים", amount: 2, unit: "גדולות" },
      { name: "שמן זית", amount: 1, unit: "כף" },
    ]),
    instructions: JSON.stringify([
      { step: 1, text: "חותך בטטה לקוביות קטנות. מבשל חלקית במיקרו 3 דקות או מרתיח עד שיהיה רך בקושי.", timerSeconds: 180 },
      { step: 2, text: "חותך סטייק, פלפל ובצל.", timerSeconds: 180 },
      { step: 3, text: "מטגן בטטה בשמן זית עד להזהבה. מוסיף פלפל ובצל, מבשל עד לריכוך.", timerSeconds: 420 },
      { step: 4, text: "דוחף את ההאש הצדה, מטגן ביצים באותה המחבת. מגיש סטייק וביצים מעל ההאש.", timerSeconds: 420 },
    ]),
    imageUrl: "https://live.staticflickr.com/5534/10363465713_e95d0e88b4_b.jpg",
    source: "קטלוג מתחילים של DayToDay",
    tags: "ארוחת בוקר,עתיר חלבון,סטייק,דשן,ארוחת צהריים מאוחרת",
  },
];

export async function seedRecipes(): Promise<number> {
  let created = 0;
  for (const recipe of SEED_RECIPES) {
    const existing = await prisma.recipe.findFirst({
      where: { title: recipe.title, ownerId: null },
    });
    if (existing) {
      if ((existing.imageUrl ?? null) !== (recipe.imageUrl ?? null)) {
        await prisma.recipe.update({
          where: { id: existing.id },
          data: { imageUrl: recipe.imageUrl ?? null },
        });
      }
      continue;
    }
    await prisma.recipe.create({
      data: {
        title: recipe.title,
        description: recipe.description ?? null,
        meal: recipe.meal,
        difficulty: recipe.difficulty,
        prepTimeMin: recipe.prepTimeMin,
        cookTimeMin: recipe.cookTimeMin,
        servings: recipe.servings,
        calories: recipe.calories,
        proteinG: recipe.proteinG,
        carbsG: recipe.carbsG,
        fatG: recipe.fatG,
        ingredients: recipe.ingredients,
        instructions: recipe.instructions,
        imageUrl: recipe.imageUrl ?? null,
        source: recipe.source ?? null,
        tags: recipe.tags ?? null,
        ownerId: null,
      },
    });
    created += 1;
  }
  return created;
}

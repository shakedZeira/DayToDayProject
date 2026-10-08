import type { ItalianCourse } from "shared";

export const ITALIAN_COURSE: ItalianCourse = {
  language: "it",
  units: [
    {
      id: "u1",
      title: "Basics",
      lessons: [
        {
          id: "u1l1",
          title: "Greetings",
          exercises: [
            { id: "u1l1e1", kind: "choice", direction: "en_to_it", prompt: "hello", choices: ["ciao", "grazie", "arrivederci", "scusi"], answerIndex: 0 },
            { id: "u1l1e2", kind: "choice", direction: "it_to_en", prompt: "Buongiorno!", choices: ["Good night", "Good morning", "Goodbye", "Thank you"], answerIndex: 1 },
            { id: "u1l1e3", kind: "type", prompt: "Write in Italian: Thank you", accepted: ["grazie"] },
            { id: "u1l1e4", kind: "match", pairs: [{ left: "ciao", right: "hello" }, { left: "grazie", right: "thank you" }, { left: "prego", right: "you're welcome" }, { left: "scusi", right: "excuse me" }] },
            { id: "u1l1e5", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Goodbye", "Hello", "Please", "Sorry"], answerIndex: 0, speak: "Arrivederci" },
            { id: "u1l1e6", kind: "choice", direction: "en_to_it", prompt: "good evening", choices: ["buonasera", "buongiorno", "buonanotte", "ciao"], answerIndex: 0 }
          ]
        },
        {
          id: "u1l2",
          title: "Introductions",
          exercises: [
            { id: "u1l2e1", kind: "choice", direction: "en_to_it", prompt: "My name is…", choices: ["Mi chiamo…", "Come stai?", "Dov'è?", "Quanto costa?"], answerIndex: 0 },
            { id: "u1l2e2", kind: "choice", direction: "it_to_en", prompt: "Come ti chiami?", choices: ["How are you?", "What is your name?", "Where are you?", "How old are you?"], answerIndex: 1 },
            { id: "u1l2e3", kind: "type", prompt: "Write in Italian: I am American", accepted: ["sono americano", "sono americana"] },
            { id: "u1l2e4", kind: "match", pairs: [{ left: "mi chiamo", right: "my name is" }, { left: "piacere", right: "nice to meet you" }, { left: "come stai", right: "how are you" }, { left: "molto bene", right: "very good" }] },
            { id: "u1l2e5", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Nice to meet you", "See you later", "How are you?", "Where are you from?"], answerIndex: 0, speak: "Piacere di conoscerti" },
            { id: "u1l2e6", kind: "choice", direction: "en_to_it", prompt: "Where are you from?", choices: ["Di dove sei?", "Che ore sono?", "Come ti chiami?", "Cosa mangi?"], answerIndex: 0 }
          ]
        },
        {
          id: "u1l3",
          title: "Polite phrases",
          exercises: [
            { id: "u1l3e1", kind: "choice", direction: "en_to_it", prompt: "please", choices: ["per favore", "grazie", "scusi", "arrivederci"], answerIndex: 0 },
            { id: "u1l3e2", kind: "choice", direction: "it_to_en", prompt: "Mi scusi", choices: ["Excuse me", "I'm sorry", "Thank you", "Please"], answerIndex: 0 },
            { id: "u1l3e3", kind: "type", prompt: "Write in Italian: Excuse me", accepted: ["mi scusi", "scusi"] },
            { id: "u1l3e4", kind: "match", pairs: [{ left: "per favore", right: "please" }, { left: "grazie mille", right: "thanks a lot" }, { left: "mi scusi", right: "excuse me" }, { left: "nessun problema", right: "no problem" }] },
            { id: "u1l3e5", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Thanks a lot", "You're welcome", "Good morning", "Goodbye"], answerIndex: 0, speak: "Grazie mille" },
            { id: "u1l3e6", kind: "choice", direction: "en_to_it", prompt: "You're welcome", choices: ["prego", "grazie", "ciao", "scusi"], answerIndex: 0 }
          ]
        }
      ]
    },
    {
      id: "u2",
      title: "Numbers & Time",
      lessons: [
        {
          id: "u2l1",
          title: "Numbers 1-10",
          exercises: [
            { id: "u2l1e1", kind: "choice", direction: "en_to_it", prompt: "five", choices: ["sette", "cinque", "dieci", "tre"], answerIndex: 1 },
            { id: "u2l1e2", kind: "choice", direction: "it_to_en", prompt: "sette", choices: ["six", "seven", "sixteen", "ten"], answerIndex: 1 },
            { id: "u2l1e3", kind: "type", prompt: "Write in Italian: three", accepted: ["tre"] },
            { id: "u2l1e4", kind: "match", pairs: [{ left: "uno", right: "one" }, { left: "due", right: "two" }, { left: "tre", right: "three" }, { left: "quattro", right: "four" }] },
            { id: "u2l1e5", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Five", "Four", "Fifteen", "Six"], answerIndex: 0, speak: "Cinque" },
            { id: "u2l1e6", kind: "choice", direction: "en_to_it", prompt: "ten", choices: ["nove", "otto", "dieci", "sette"], answerIndex: 2 }
          ]
        },
        {
          id: "u2l2",
          title: "How much does it cost?",
          exercises: [
            { id: "u2l2e1", kind: "choice", direction: "en_to_it", prompt: "How much does it cost?", choices: ["Quanto costa?", "Che ore sono?", "Dov'è?", "Come stai?"], answerIndex: 0 },
            { id: "u2l2e2", kind: "type", prompt: "Write in Italian: it costs 5 euros", accepted: ["costa 5 euro", "costa cinque euro", "costa 5€"] },
            { id: "u2l2e3", kind: "match", pairs: [{ left: "quanto costa", right: "how much does it cost" }, { left: "euro", right: "euro" }, { left: "caro", right: "expensive" }, { left: "economico", right: "cheap" }] },
            { id: "u2l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["How much does it cost?", "What time is it?", "Where is it?", "How are you?"], answerIndex: 0, speak: "Quanto costa?" },
            { id: "u2l2e5", kind: "choice", direction: "it_to_en", prompt: "È caro", choices: ["It's cheap", "It's expensive", "It's open", "It's late"], answerIndex: 1 },
            { id: "u2l2e6", kind: "choice", direction: "en_to_it", prompt: "cheap", choices: ["caro", "economico", "costoso", "nuovo"], answerIndex: 1 }
          ]
        },
        {
          id: "u2l3",
          title: "What time is it?",
          exercises: [
            { id: "u2l3e1", kind: "choice", direction: "en_to_it", prompt: "What time is it?", choices: ["Che ore sono?", "Che giorno è?", "Quanto costa?", "Come stai?"], answerIndex: 0 },
            { id: "u2l3e2", kind: "type", prompt: "Write in Italian: it is two o'clock", accepted: ["sono le due", "sono le 2"] },
            { id: "u2l3e3", kind: "match", pairs: [{ left: "oggi", right: "today" }, { left: "domani", right: "tomorrow" }, { left: "mezzogiorno", right: "noon" }, { left: "mezzanotte", right: "midnight" }] },
            { id: "u2l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["It is three o'clock", "It is two o'clock", "It is twelve o'clock", "It is four o'clock"], answerIndex: 0, speak: "Sono le tre" },
            { id: "u2l3e5", kind: "choice", direction: "it_to_en", prompt: "Che ore sono?", choices: ["What day is it?", "What time is it?", "How much is it?", "Where is it?"], answerIndex: 1 },
            { id: "u2l3e6", kind: "choice", direction: "en_to_it", prompt: "tomorrow", choices: ["oggi", "domani", "ieri", "domenica"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u3",
      title: "Food & Drink",
      lessons: [
        {
          id: "u3l1",
          title: "At the café",
          exercises: [
            { id: "u3l1e1", kind: "choice", direction: "en_to_it", prompt: "I would like a coffee", choices: ["Vorrei un caffè", "Vorrei un acqua", "Ho un caffè", "Un caffè, grazie"], answerIndex: 0 },
            { id: "u3l1e2", kind: "type", prompt: "Write in Italian: the bill please", accepted: ["il conto per favore", "il conto, per favore"] },
            { id: "u3l1e3", kind: "match", pairs: [{ left: "un caffè", right: "a coffee" }, { left: "un cappuccino", right: "a cappuccino" }, { left: "un cornetto", right: "a croissant" }, { left: "il conto", right: "the bill" }] },
            { id: "u3l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["A cappuccino, please", "A coffee, please", "The bill, please", "A glass of water"], answerIndex: 0, speak: "Un cappuccino, per favore" },
            { id: "u3l1e5", kind: "choice", direction: "it_to_en", prompt: "Vorrei un caffè", choices: ["I have a coffee", "I would like a coffee", "I drink a coffee", "I would like a tea"], answerIndex: 1 },
            { id: "u3l1e6", kind: "choice", direction: "en_to_it", prompt: "a glass of water", choices: ["una tazza d'acqua", "un bicchiere d'acqua", "un litro d'acqua", "una bottiglia d'acqua"], answerIndex: 1 }
          ]
        },
        {
          id: "u3l2",
          title: "Ordering food",
          exercises: [
            { id: "u3l2e1", kind: "choice", direction: "en_to_it", prompt: "pizza", choices: ["il pomodoro", "la pizza", "la pasta", "il pane"], answerIndex: 1 },
            { id: "u3l2e2", kind: "type", prompt: "Write in Italian: the bread", accepted: ["il pane"] },
            { id: "u3l2e3", kind: "match", pairs: [{ left: "la pasta", right: "the pasta" }, { left: "la zuppa", right: "the soup" }, { left: "delizioso", right: "delicious" }, { left: "salato", right: "salty" }] },
            { id: "u3l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The soup", "The bread", "The pasta", "The pizza"], answerIndex: 2, speak: "La pasta" },
            { id: "u3l2e5", kind: "choice", direction: "it_to_en", prompt: "La zuppa", choices: ["The salad", "The soup", "The pasta", "The fish"], answerIndex: 1 },
            { id: "u3l2e6", kind: "choice", direction: "en_to_it", prompt: "delicious", choices: ["delizioso", "salato", "dolce", "stanco"], answerIndex: 0 }
          ]
        },
        {
          id: "u3l3",
          title: "Tastes & ingredients",
          exercises: [
            { id: "u3l3e1", kind: "choice", direction: "en_to_it", prompt: "the cheese", choices: ["il pomodoro", "il formaggio", "il burro", "l'uovo"], answerIndex: 1 },
            { id: "u3l3e2", kind: "type", prompt: "Write in Italian: the milk", accepted: ["il latte"] },
            { id: "u3l3e3", kind: "match", pairs: [{ left: "il pomodoro", right: "the tomato" }, { left: "l'aglio", right: "the garlic" }, { left: "il burro", right: "the butter" }, { left: "l'uovo", right: "the egg" }] },
            { id: "u3l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The butter", "The milk", "The cheese", "The egg"], answerIndex: 2, speak: "Il formaggio" },
            { id: "u3l3e5", kind: "choice", direction: "it_to_en", prompt: "L'aglio", choices: ["The onion", "The garlic", "The salt", "The oil"], answerIndex: 1 },
            { id: "u3l3e6", kind: "choice", direction: "en_to_it", prompt: "the egg", choices: ["il pomodoro", "l'uovo", "il latte", "il pane"], answerIndex: 1 }
          ]
        }
      ]
    }
  ]
};

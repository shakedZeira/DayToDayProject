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
            { id: "u3l3e3", kind: "match", pairs: [{ left: "il pomodoro", right: "the tomato" }, { left: "l'aglio", right: "the garlic" }, { left: "il burro", right: "the butter" }, { left: "l'uovo", right: "the egg" }] },
            { id: "u3l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The butter", "The milk", "The cheese", "The egg"], answerIndex: 2, speak: "Il formaggio" },
            { id: "u3l3e5", kind: "choice", direction: "it_to_en", prompt: "L'aglio", choices: ["The onion", "The garlic", "The salt", "The oil"], answerIndex: 1 },
            { id: "u3l3e6", kind: "choice", direction: "en_to_it", prompt: "the egg", choices: ["il pomodoro", "l'uovo", "il latte", "il pane"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u4",
      title: "Family & People",
      lessons: [
        {
          id: "u4l1",
          title: "Family",
          exercises: [
            { id: "u4l1e1", kind: "choice", direction: "en_to_it", prompt: "the mother", choices: ["la sorella", "la madre", "il nonno", "il fratello"], answerIndex: 1 },
            { id: "u4l1e2", kind: "choice", direction: "it_to_en", prompt: "il padre", choices: ["The daughter", "The mother", "The father", "The son"], answerIndex: 2 },
            { id: "u4l1e3", kind: "match", pairs: [{ left: "il fratello", right: "the brother" }, { left: "la sorella", right: "the sister" }, { left: "il figlio", right: "the son" }, { left: "la figlia", right: "the daughter" }] },
            { id: "u4l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The grandfather", "The father", "The son", "The mother"], answerIndex: 0, speak: "Il nonno" },
            { id: "u4l1e5", kind: "choice", direction: "en_to_it", prompt: "the grandmother", choices: ["la sorella", "la nonna", "il nonno", "il fratello"], answerIndex: 1 },
            { id: "u4l1e6", kind: "choice", direction: "it_to_en", prompt: "il figlio", choices: ["The grandfather", "The father", "The son", "The daughter"], answerIndex: 2 }
          ]
        },
        {
          id: "u4l2",
          title: "People",
          exercises: [
            { id: "u4l2e1", kind: "choice", direction: "en_to_it", prompt: "the man", choices: ["l'amica", "la donna", "l'uomo", "l'amico"], answerIndex: 2 },
            { id: "u4l2e2", kind: "choice", direction: "it_to_en", prompt: "la donna", choices: ["The young woman", "The little girl", "The young man", "The woman"], answerIndex: 3 },
            { id: "u4l2e3", kind: "match", pairs: [{ left: "il bambino", right: "the little boy" }, { left: "la bambina", right: "the little girl" }, { left: "l'amico", right: "the male friend" }, { left: "l'amica", right: "the female friend" }] },
            { id: "u4l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The little girl", "The young man", "The young woman", "The little boy"], answerIndex: 1, speak: "Il ragazzo" },
            { id: "u4l2e5", kind: "choice", direction: "en_to_it", prompt: "the young woman", choices: ["l'amica", "la donna", "la ragazza", "l'amico"], answerIndex: 2 },
            { id: "u4l2e6", kind: "choice", direction: "it_to_en", prompt: "l'amico", choices: ["The man", "The little girl", "The young woman", "The male friend"], answerIndex: 3 }
          ]
        },
        {
          id: "u4l3",
          title: "Describing people",
          exercises: [
            { id: "u4l3e1", kind: "choice", direction: "en_to_it", prompt: "tall", choices: ["triste", "vecchio", "felice", "alto"], answerIndex: 3 },
            { id: "u4l3e2", kind: "choice", direction: "it_to_en", prompt: "basso", choices: ["Short", "Young", "Kind", "Tall"], answerIndex: 0 },
            { id: "u4l3e3", kind: "match", pairs: [{ left: "giovane", right: "young" }, { left: "vecchio", right: "old" }, { left: "simpatico", right: "friendly" }, { left: "gentile", right: "kind" }] },
            { id: "u4l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Kind", "Short", "Happy", "Friendly"], answerIndex: 2, speak: "Felice" },
            { id: "u4l3e5", kind: "choice", direction: "en_to_it", prompt: "sad", choices: ["alto", "vecchio", "felice", "triste"], answerIndex: 3 },
            { id: "u4l3e6", kind: "choice", direction: "it_to_en", prompt: "simpatico", choices: ["Friendly", "Young", "Happy", "Short"], answerIndex: 0 }
          ]
        },
        {
          id: "u4l4",
          title: "My family",
          exercises: [
            { id: "u4l4e1", kind: "choice", direction: "en_to_it", prompt: "I have a sister", choices: ["Ho una sorella", "Mia madre è alta", "Questa è mia nonna", "Ho un fratello"], answerIndex: 0 },
            { id: "u4l4e2", kind: "choice", direction: "it_to_en", prompt: "Ho un fratello", choices: ["I have two children", "I have a brother", "We are a family", "My father is kind"], answerIndex: 1 },
            { id: "u4l4e3", kind: "match", pairs: [{ left: "mia madre è alta", right: "my mother is tall" }, { left: "mio padre è gentile", right: "my father is kind" }, { left: "ho due figli", right: "i have two children" }, { left: "questa è mia nonna", right: "this is my grandmother" }] },
            { id: "u4l4e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["I have a sister", "My father is kind", "We are a family", "What is your brother's name?"], answerIndex: 3, speak: "Come si chiama tuo fratello?" },
            { id: "u4l4e5", kind: "choice", direction: "en_to_it", prompt: "We are a family", choices: ["Siamo una famiglia", "Mia madre è alta", "Questa è mia nonna", "Ho un fratello"], answerIndex: 0 },
            { id: "u4l4e6", kind: "choice", direction: "it_to_en", prompt: "Ho due figli", choices: ["This is my grandmother", "I have two children", "I have a sister", "My father is kind"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u5",
      title: "Daily Life",
      lessons: [
        {
          id: "u5l1",
          title: "Days of the week",
          exercises: [
            { id: "u5l1e1", kind: "choice", direction: "en_to_it", prompt: "Monday", choices: ["venerdì", "lunedì", "la settimana", "giovedì"], answerIndex: 1 },
            { id: "u5l1e2", kind: "choice", direction: "it_to_en", prompt: "martedì", choices: ["Sunday", "Wednesday", "Tuesday", "Saturday"], answerIndex: 2 },
            { id: "u5l1e3", kind: "match", pairs: [{ left: "mercoledì", right: "wednesday" }, { left: "giovedì", right: "thursday" }, { left: "venerdì", right: "friday" }, { left: "sabato", right: "saturday" }] },
            { id: "u5l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Sunday", "Wednesday", "Saturday", "Tuesday"], answerIndex: 0, speak: "Domenica" },
            { id: "u5l1e5", kind: "choice", direction: "en_to_it", prompt: "the week", choices: ["venerdì", "la settimana", "lunedì", "giovedì"], answerIndex: 1 },
            { id: "u5l1e6", kind: "choice", direction: "it_to_en", prompt: "venerdì", choices: ["The week", "Wednesday", "Friday", "Sunday"], answerIndex: 2 }
          ]
        },
        {
          id: "u5l2",
          title: "Months",
          exercises: [
            { id: "u5l2e1", kind: "choice", direction: "en_to_it", prompt: "January", choices: ["luglio", "marzo", "gennaio", "giugno"], answerIndex: 2 },
            { id: "u5l2e2", kind: "choice", direction: "it_to_en", prompt: "febbraio", choices: ["January", "May", "August", "February"], answerIndex: 3 },
            { id: "u5l2e3", kind: "match", pairs: [{ left: "marzo", right: "march" }, { left: "aprile", right: "april" }, { left: "maggio", right: "may" }, { left: "giugno", right: "june" }] },
            { id: "u5l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["May", "July", "January", "April"], answerIndex: 1, speak: "Luglio" },
            { id: "u5l2e5", kind: "choice", direction: "en_to_it", prompt: "August", choices: ["luglio", "marzo", "agosto", "giugno"], answerIndex: 2 },
            { id: "u5l2e6", kind: "choice", direction: "it_to_en", prompt: "maggio", choices: ["February", "June", "January", "May"], answerIndex: 3 }
          ]
        },
        {
          id: "u5l3",
          title: "Colors",
          exercises: [
            { id: "u5l3e1", kind: "choice", direction: "en_to_it", prompt: "red", choices: ["blu", "nero", "rosa", "rosso"], answerIndex: 3 },
            { id: "u5l3e2", kind: "choice", direction: "it_to_en", prompt: "blu", choices: ["Blue", "Yellow", "Orange", "Green"], answerIndex: 0 },
            { id: "u5l3e3", kind: "match", pairs: [{ left: "verde", right: "green" }, { left: "giallo", right: "yellow" }, { left: "nero", right: "black" }, { left: "bianco", right: "white" }] },
            { id: "u5l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Pink", "Green", "Orange", "White"], answerIndex: 2, speak: "Arancione" },
            { id: "u5l3e5", kind: "choice", direction: "en_to_it", prompt: "pink", choices: ["blu", "nero", "rosso", "rosa"], answerIndex: 3 },
            { id: "u5l3e6", kind: "choice", direction: "it_to_en", prompt: "nero", choices: ["Black", "Yellow", "Pink", "Green"], answerIndex: 0 }
          ]
        },
        {
          id: "u5l4",
          title: "Weather",
          exercises: [
            { id: "u5l4e1", kind: "choice", direction: "en_to_it", prompt: "the sun", choices: ["il sole", "il vento", "freddo", "la neve"], answerIndex: 0 },
            { id: "u5l4e2", kind: "choice", direction: "it_to_en", prompt: "la pioggia", choices: ["Hot", "The rain", "The sun", "The cloud"], answerIndex: 1 },
            { id: "u5l4e3", kind: "match", pairs: [{ left: "la neve", right: "the snow" }, { left: "il vento", right: "the wind" }, { left: "la nuvola", right: "the cloud" }, { left: "caldo", right: "hot" }] },
            { id: "u5l4e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The rain", "The cloud", "The sun", "Cold"], answerIndex: 3, speak: "Freddo" },
            { id: "u5l4e5", kind: "choice", direction: "en_to_it", prompt: "the sky", choices: ["il cielo", "il vento", "freddo", "la neve"], answerIndex: 0 },
            { id: "u5l4e6", kind: "choice", direction: "it_to_en", prompt: "la nuvola", choices: ["Cold", "The cloud", "The rain", "Hot"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u6",
      title: "Around Town",
      lessons: [
        {
          id: "u6l1",
          title: "Places",
          exercises: [
            { id: "u6l1e1", kind: "choice", direction: "en_to_it", prompt: "the house", choices: ["la farmacia", "la casa", "la scuola", "la banca"], answerIndex: 1 },
            { id: "u6l1e2", kind: "choice", direction: "it_to_en", prompt: "la scuola", choices: ["The church", "The shop", "The school", "The hospital"], answerIndex: 2 },
            { id: "u6l1e3", kind: "match", pairs: [{ left: "l'ufficio", right: "the office" }, { left: "il negozio", right: "the shop" }, { left: "la banca", right: "the bank" }, { left: "la farmacia", right: "the pharmacy" }] },
            { id: "u6l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The hospital", "The shop", "The church", "The office"], answerIndex: 0, speak: "L'ospedale" },
            { id: "u6l1e5", kind: "choice", direction: "en_to_it", prompt: "the church", choices: ["la farmacia", "la chiesa", "la scuola", "la banca"], answerIndex: 1 },
            { id: "u6l1e6", kind: "choice", direction: "it_to_en", prompt: "la banca", choices: ["The house", "The shop", "The bank", "The church"], answerIndex: 2 }
          ]
        },
        {
          id: "u6l2",
          title: "Directions",
          exercises: [
            { id: "u6l2e1", kind: "choice", direction: "en_to_it", prompt: "to the right", choices: ["davanti", "vicino", "a destra", "là"], answerIndex: 2 },
            { id: "u6l2e2", kind: "choice", direction: "it_to_en", prompt: "a sinistra", choices: ["Straight ahead", "Here", "To the right", "To the left"], answerIndex: 3 },
            { id: "u6l2e3", kind: "match", pairs: [{ left: "dritto", right: "straight ahead" }, { left: "vicino", right: "near" }, { left: "lontano", right: "far" }, { left: "qui", right: "here" }] },
            { id: "u6l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Here", "There", "To the left", "Far"], answerIndex: 1, speak: "Là" },
            { id: "u6l2e5", kind: "choice", direction: "en_to_it", prompt: "in front", choices: ["a destra", "vicino", "davanti", "là"], answerIndex: 2 },
            { id: "u6l2e6", kind: "choice", direction: "it_to_en", prompt: "lontano", choices: ["Straight ahead", "There", "To the left", "Far"], answerIndex: 3 }
          ]
        },
        {
          id: "u6l3",
          title: "Transport",
          exercises: [
            { id: "u6l3e1", kind: "choice", direction: "en_to_it", prompt: "the train", choices: ["la macchina", "il taxi", "l'autobus", "il treno"], answerIndex: 3 },
            { id: "u6l3e2", kind: "choice", direction: "it_to_en", prompt: "l'autobus", choices: ["The bus", "The bicycle", "The ticket", "The airplane"], answerIndex: 0 },
            { id: "u6l3e3", kind: "match", pairs: [{ left: "la macchina", right: "the car" }, { left: "l'aereo", right: "the airplane" }, { left: "la bicicletta", right: "the bicycle" }, { left: "il taxi", right: "the taxi" }] },
            { id: "u6l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The train", "The airplane", "The station", "The ticket"], answerIndex: 2, speak: "La stazione" },
            { id: "u6l3e5", kind: "choice", direction: "en_to_it", prompt: "the ticket", choices: ["la macchina", "il taxi", "l'autobus", "il biglietto"], answerIndex: 3 },
            { id: "u6l3e6", kind: "choice", direction: "it_to_en", prompt: "la bicicletta", choices: ["The bicycle", "The taxi", "The train", "The airplane"], answerIndex: 0 }
          ]
        },
        {
          id: "u6l4",
          title: "Travel",
          exercises: [
            { id: "u6l4e1", kind: "choice", direction: "en_to_it", prompt: "the hotel", choices: ["l'albergo", "il passaporto", "la strada", "la valigia"], answerIndex: 0 },
            { id: "u6l4e2", kind: "choice", direction: "it_to_en", prompt: "la camera", choices: ["The city", "The room", "The key", "The map"], answerIndex: 1 },
            { id: "u6l4e3", kind: "match", pairs: [{ left: "la chiave", right: "the key" }, { left: "la valigia", right: "the suitcase" }, { left: "il passaporto", right: "the passport" }, { left: "la mappa", right: "the map" }] },
            { id: "u6l4e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The key", "The map", "The room", "The city"], answerIndex: 3, speak: "La città" },
            { id: "u6l4e5", kind: "choice", direction: "en_to_it", prompt: "the street", choices: ["la strada", "il passaporto", "l'albergo", "la valigia"], answerIndex: 0 },
            { id: "u6l4e6", kind: "choice", direction: "it_to_en", prompt: "il passaporto", choices: ["The street", "The passport", "The key", "The city"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u7",
      title: "More Food",
      lessons: [
        {
          id: "u7l1",
          title: "Drinks",
          exercises: [
            { id: "u7l1e1", kind: "choice", direction: "en_to_it", prompt: "the water", choices: ["la limonata", "l'acqua", "la birra", "il tè"], answerIndex: 1 },
            { id: "u7l1e2", kind: "choice", direction: "it_to_en", prompt: "il vino", choices: ["The water", "The juice", "The wine", "The coffee"], answerIndex: 2 },
            { id: "u7l1e3", kind: "match", pairs: [{ left: "la birra", right: "the beer" }, { left: "il latte", right: "the milk" }, { left: "il succo", right: "the juice" }, { left: "il tè", right: "the tea" }] },
            { id: "u7l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The lemonade", "The juice", "The water", "The milk"], answerIndex: 0, speak: "La limonata" },
            { id: "u7l1e5", kind: "choice", direction: "en_to_it", prompt: "the coffee", choices: ["la limonata", "il caffè", "la birra", "il tè"], answerIndex: 1 },
            { id: "u7l1e6", kind: "choice", direction: "it_to_en", prompt: "il succo", choices: ["The wine", "The tea", "The juice", "The water"], answerIndex: 2 }
          ]
        },
        {
          id: "u7l2",
          title: "Fruit",
          exercises: [
            { id: "u7l2e1", kind: "choice", direction: "en_to_it", prompt: "the apple", choices: ["la banana", "il limone", "la mela", "la pesca"], answerIndex: 2 },
            { id: "u7l2e2", kind: "choice", direction: "it_to_en", prompt: "la banana", choices: ["The strawberry", "The pear", "The orange", "The banana"], answerIndex: 3 },
            { id: "u7l2e3", kind: "match", pairs: [{ left: "l'arancia", right: "the orange" }, { left: "la fragola", right: "the strawberry" }, { left: "il limone", right: "the lemon" }, { left: "l'uva", right: "the grapes" }] },
            { id: "u7l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The peach", "The pear", "The orange", "The grapes"], answerIndex: 1, speak: "La pera" },
            { id: "u7l2e5", kind: "choice", direction: "en_to_it", prompt: "the peach", choices: ["la banana", "il limone", "la pesca", "la mela"], answerIndex: 2 },
            { id: "u7l2e6", kind: "choice", direction: "it_to_en", prompt: "il limone", choices: ["The strawberry", "The peach", "The orange", "The lemon"], answerIndex: 3 }
          ]
        },
        {
          id: "u7l3",
          title: "Vegetables",
          exercises: [
            { id: "u7l3e1", kind: "choice", direction: "en_to_it", prompt: "the carrot", choices: ["l'insalata", "la zucchina", "la cipolla", "la carota"], answerIndex: 3 },
            { id: "u7l3e2", kind: "choice", direction: "it_to_en", prompt: "la patata", choices: ["The potato", "The bell pepper", "The carrot", "The mushroom"], answerIndex: 0 },
            { id: "u7l3e3", kind: "match", pairs: [{ left: "la cipolla", right: "the onion" }, { left: "l'insalata", right: "the salad" }, { left: "il fungo", right: "the mushroom" }, { left: "il peperone", right: "the bell pepper" }] },
            { id: "u7l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The potato", "The mushroom", "The zucchini", "The carrot"], answerIndex: 2, speak: "La zucchina" },
            { id: "u7l3e5", kind: "choice", direction: "en_to_it", prompt: "the eggplant", choices: ["l'insalata", "la zucchina", "la cipolla", "la melanzana"], answerIndex: 3 },
            { id: "u7l3e6", kind: "choice", direction: "it_to_en", prompt: "il fungo", choices: ["The mushroom", "The zucchini", "The potato", "The bell pepper"], answerIndex: 0 }
          ]
        },
        {
          id: "u7l4",
          title: "At the restaurant",
          exercises: [
            { id: "u7l4e1", kind: "choice", direction: "en_to_it", prompt: "the menu", choices: ["il menu", "la cena", "il tavolo", "il pranzo"], answerIndex: 0 },
            { id: "u7l4e2", kind: "choice", direction: "it_to_en", prompt: "il tavolo", choices: ["The fork", "The table", "Breakfast", "The dessert"], answerIndex: 1 },
            { id: "u7l4e3", kind: "match", pairs: [{ left: "il cameriere", right: "the waiter" }, { left: "la colazione", right: "breakfast" }, { left: "il pranzo", right: "lunch" }, { left: "la cena", right: "dinner" }] },
            { id: "u7l4e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Breakfast", "The fork", "The waiter", "The dessert"], answerIndex: 3, speak: "Il dolce" },
            { id: "u7l4e5", kind: "choice", direction: "en_to_it", prompt: "the fork", choices: ["la forchetta", "la cena", "il tavolo", "il pranzo"], answerIndex: 0 },
            { id: "u7l4e6", kind: "choice", direction: "it_to_en", prompt: "il pranzo", choices: ["The menu", "Lunch", "Breakfast", "The fork"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u8",
      title: "Verbs & Actions",
      lessons: [
        {
          id: "u8l1",
          title: "Everyday verbs",
          exercises: [
            { id: "u8l1e1", kind: "choice", direction: "en_to_it", prompt: "to eat", choices: ["correre", "mangiare", "parlare", "scrivere"], answerIndex: 1 },
            { id: "u8l1e2", kind: "choice", direction: "it_to_en", prompt: "bere", choices: ["To sleep", "To read", "To drink", "To eat"], answerIndex: 2 },
            { id: "u8l1e3", kind: "match", pairs: [{ left: "dormire", right: "to sleep" }, { left: "parlare", right: "to speak" }, { left: "camminare", right: "to walk" }, { left: "leggere", right: "to read" }] },
            { id: "u8l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["To write", "To read", "To drink", "To walk"], answerIndex: 0, speak: "Scrivere" },
            { id: "u8l1e5", kind: "choice", direction: "en_to_it", prompt: "to run", choices: ["mangiare", "correre", "parlare", "scrivere"], answerIndex: 1 },
            { id: "u8l1e6", kind: "choice", direction: "it_to_en", prompt: "camminare", choices: ["To sleep", "To write", "To walk", "To drink"], answerIndex: 2 }
          ]
        },
        {
          id: "u8l2",
          title: "More verbs",
          exercises: [
            { id: "u8l2e1", kind: "choice", direction: "en_to_it", prompt: "to go", choices: ["fare", "volere", "andare", "venire"], answerIndex: 2 },
            { id: "u8l2e2", kind: "choice", direction: "it_to_en", prompt: "venire", choices: ["To hear", "To know", "To see", "To come"], answerIndex: 3 },
            { id: "u8l2e3", kind: "match", pairs: [{ left: "fare", right: "to do" }, { left: "vedere", right: "to see" }, { left: "sentire", right: "to hear" }, { left: "volere", right: "to want" }] },
            { id: "u8l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["To go", "To be able to", "To see", "To know"], answerIndex: 1, speak: "Potere" },
            { id: "u8l2e5", kind: "choice", direction: "en_to_it", prompt: "to know", choices: ["fare", "volere", "sapere", "venire"], answerIndex: 2 },
            { id: "u8l2e6", kind: "choice", direction: "it_to_en", prompt: "sentire", choices: ["To want", "To go", "To see", "To hear"], answerIndex: 3 }
          ]
        },
        {
          id: "u8l3",
          title: "I do things",
          exercises: [
            { id: "u8l3e1", kind: "choice", direction: "en_to_it", prompt: "I speak Italian", choices: ["Leggo un libro", "Non posso venire", "Dormo molto", "Parlo italiano"], answerIndex: 3 },
            { id: "u8l3e2", kind: "choice", direction: "it_to_en", prompt: "Mangio una mela", choices: ["I eat an apple", "I want a coffee", "I drink water", "I am going home"], answerIndex: 0 },
            { id: "u8l3e3", kind: "match", pairs: [{ left: "bevo l'acqua", right: "i drink water" }, { left: "dormo molto", right: "i sleep a lot" }, { left: "leggo un libro", right: "i read a book" }, { left: "vado a casa", right: "i am going home" }] },
            { id: "u8l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["I drink water", "I am going home", "I want a coffee", "I eat an apple"], answerIndex: 2, speak: "Voglio un caffè" },
            { id: "u8l3e5", kind: "choice", direction: "en_to_it", prompt: "I cannot come", choices: ["Leggo un libro", "Parlo italiano", "Dormo molto", "Non posso venire"], answerIndex: 3 },
            { id: "u8l3e6", kind: "choice", direction: "it_to_en", prompt: "Leggo un libro", choices: ["I read a book", "I cannot come", "I drink water", "I want a coffee"], answerIndex: 0 }
          ]
        },
        {
          id: "u8l4",
          title: "To be & to have",
          exercises: [
            { id: "u8l4e1", kind: "choice", direction: "en_to_it", prompt: "I am", choices: ["sono", "hai", "è", "ho"], answerIndex: 0 },
            { id: "u8l4e2", kind: "choice", direction: "it_to_en", prompt: "sei", choices: ["I am", "You are", "You all are", "We have"], answerIndex: 1 },
            { id: "u8l4e3", kind: "match", pairs: [{ left: "è", right: "he is" }, { left: "siamo", right: "we are" }, { left: "siete", right: "you all are" }, { left: "ho", right: "i have" }] },
            { id: "u8l4e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["You all are", "I am", "We are", "You have"], answerIndex: 3, speak: "Hai" },
            { id: "u8l4e5", kind: "choice", direction: "en_to_it", prompt: "we have", choices: ["abbiamo", "hai", "è", "ho"], answerIndex: 0 },
            { id: "u8l4e6", kind: "choice", direction: "it_to_en", prompt: "siete", choices: ["You are", "You all are", "I have", "I am"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u9",
      title: "Shopping & Time",
      lessons: [
        {
          id: "u9l1",
          title: "Clothes",
          exercises: [
            { id: "u9l1e1", kind: "choice", direction: "en_to_it", prompt: "the shirt", choices: ["i pantaloni", "la camicia", "il vestito", "i calzini"], answerIndex: 1 },
            { id: "u9l1e2", kind: "choice", direction: "it_to_en", prompt: "i pantaloni", choices: ["The jacket", "The hat", "The pants", "The shoes"], answerIndex: 2 },
            { id: "u9l1e3", kind: "match", pairs: [{ left: "le scarpe", right: "the shoes" }, { left: "la giacca", right: "the jacket" }, { left: "il vestito", right: "the dress" }, { left: "la gonna", right: "the skirt" }] },
            { id: "u9l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The hat", "The socks", "The shoes", "The skirt"], answerIndex: 0, speak: "Il cappello" },
            { id: "u9l1e5", kind: "choice", direction: "en_to_it", prompt: "the socks", choices: ["i pantaloni", "i calzini", "il vestito", "la camicia"], answerIndex: 1 },
            { id: "u9l1e6", kind: "choice", direction: "it_to_en", prompt: "il vestito", choices: ["The jacket", "The socks", "The dress", "The shoes"], answerIndex: 2 }
          ]
        },
        {
          id: "u9l2",
          title: "Shopping",
          exercises: [
            { id: "u9l2e1", kind: "choice", direction: "en_to_it", prompt: "Can I try it on?", choices: ["È troppo caro", "Accettate carte?", "Posso provare?", "Quanto costa questo?"], answerIndex: 2 },
            { id: "u9l2e2", kind: "choice", direction: "it_to_en", prompt: "Ho bisogno di aiuto", choices: ["I will take this", "Can I try it on?", "That is fine", "I need help"], answerIndex: 3 },
            { id: "u9l2e3", kind: "match", pairs: [{ left: "quanto costa questo", right: "how much is this" }, { left: "è troppo caro", right: "it is too expensive" }, { left: "va bene", right: "that is fine" }, { left: "prendo questo", right: "i will take this" }] },
            { id: "u9l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["I need help", "Do you take cards?", "That is fine", "Can I try it on?"], answerIndex: 1, speak: "Accettate carte?" },
            { id: "u9l2e5", kind: "choice", direction: "en_to_it", prompt: "Here is your change", choices: ["È troppo caro", "Accettate carte?", "Ecco il resto", "Quanto costa questo?"], answerIndex: 2 },
            { id: "u9l2e6", kind: "choice", direction: "it_to_en", prompt: "Va bene", choices: ["Do you take cards?", "I need help", "I will take this", "That is fine"], answerIndex: 3 }
          ]
        },
        {
          id: "u9l3",
          title: "Numbers 11-100",
          exercises: [
            { id: "u9l3e1", kind: "choice", direction: "en_to_it", prompt: "eleven", choices: ["quaranta", "dodici", "trenta", "undici"], answerIndex: 3 },
            { id: "u9l3e2", kind: "choice", direction: "it_to_en", prompt: "dodici", choices: ["Twelve", "One hundred", "Twenty", "Fifty"], answerIndex: 0 },
            { id: "u9l3e3", kind: "match", pairs: [{ left: "quindici", right: "fifteen" }, { left: "venti", right: "twenty" }, { left: "trenta", right: "thirty" }, { left: "quaranta", right: "forty" }] },
            { id: "u9l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Twenty", "One hundred", "Fifty", "Fifteen"], answerIndex: 2, speak: "Cinquanta" },
            { id: "u9l3e5", kind: "choice", direction: "en_to_it", prompt: "one hundred", choices: ["quaranta", "dodici", "trenta", "cento"], answerIndex: 3 },
            { id: "u9l3e6", kind: "choice", direction: "it_to_en", prompt: "trenta", choices: ["Thirty", "Eleven", "Twenty", "One hundred"], answerIndex: 0 }
          ]
        },
        {
          id: "u9l4",
          title: "Time of day",
          exercises: [
            { id: "u9l4e1", kind: "choice", direction: "en_to_it", prompt: "the morning", choices: ["la mattina", "tardi", "la notte", "presto"], answerIndex: 0 },
            { id: "u9l4e2", kind: "choice", direction: "it_to_en", prompt: "il pomeriggio", choices: ["The evening", "The afternoon", "The minute", "The morning"], answerIndex: 1 },
            { id: "u9l4e3", kind: "match", pairs: [{ left: "la sera", right: "the evening" }, { left: "la notte", right: "the night" }, { left: "l'ora", right: "the hour" }, { left: "il minuto", right: "the minute" }] },
            { id: "u9l4e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The minute", "The afternoon", "The hour", "Early"], answerIndex: 3, speak: "Presto" },
            { id: "u9l4e5", kind: "choice", direction: "en_to_it", prompt: "late", choices: ["tardi", "la mattina", "la notte", "presto"], answerIndex: 0 },
            { id: "u9l4e6", kind: "choice", direction: "it_to_en", prompt: "l'ora", choices: ["The evening", "The hour", "Early", "The afternoon"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u10",
      title: "Body & Health",
      lessons: [
        {
          id: "u10l1",
          title: "Body",
          exercises: [
            { id: "u10l1e1", kind: "choice", direction: "en_to_it", prompt: "the head", choices: ["il piede", "la testa", "la bocca", "la mano"], answerIndex: 1 },
            { id: "u10l1e2", kind: "choice", direction: "it_to_en", prompt: "la mano", choices: ["The nose", "The leg", "The hand", "The eye"], answerIndex: 2 },
            { id: "u10l1e3", kind: "match", pairs: [{ left: "il piede", right: "the foot" }, { left: "l'occhio", right: "the eye" }, { left: "il naso", right: "the nose" }, { left: "la bocca", right: "the mouth" }] },
            { id: "u10l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The arm", "The head", "The eye", "The leg"], answerIndex: 0, speak: "Il braccio" },
            { id: "u10l1e5", kind: "choice", direction: "en_to_it", prompt: "the leg", choices: ["il piede", "la gamba", "la bocca", "la mano"], answerIndex: 1 },
            { id: "u10l1e6", kind: "choice", direction: "it_to_en", prompt: "il naso", choices: ["The mouth", "The head", "The nose", "The eye"], answerIndex: 2 }
          ]
        },
        {
          id: "u10l2",
          title: "How do you feel?",
          exercises: [
            { id: "u10l2e1", kind: "choice", direction: "en_to_it", prompt: "I feel well", choices: ["Ho sonno", "Sono stanco", "Sto bene", "Ho sete"], answerIndex: 2 },
            { id: "u10l2e2", kind: "choice", direction: "it_to_en", prompt: "Sto male", choices: ["I am hot", "I am hungry", "I am cold", "I feel sick"], answerIndex: 3 },
            { id: "u10l2e3", kind: "match", pairs: [{ left: "ho fame", right: "i am hungry" }, { left: "ho sete", right: "i am thirsty" }, { left: "ho sonno", right: "i am sleepy" }, { left: "ho freddo", right: "i am cold" }] },
            { id: "u10l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["I am hungry", "I am hot", "I am cold", "I feel sick"], answerIndex: 1, speak: "Ho caldo" },
            { id: "u10l2e5", kind: "choice", direction: "en_to_it", prompt: "I am tired", choices: ["Ho sonno", "Sto bene", "Sono stanco", "Ho sete"], answerIndex: 2 },
            { id: "u10l2e6", kind: "choice", direction: "it_to_en", prompt: "Ho sonno", choices: ["I am tired", "I am hungry", "I am hot", "I am sleepy"], answerIndex: 3 }
          ]
        },
        {
          id: "u10l3",
          title: "At the doctor",
          exercises: [
            { id: "u10l3e1", kind: "choice", direction: "en_to_it", prompt: "the doctor", choices: ["Ho mal di testa", "il dolore", "la tosse", "il medico"], answerIndex: 3 },
            { id: "u10l3e2", kind: "choice", direction: "it_to_en", prompt: "la medicina", choices: ["The medicine", "The doctor", "The common cold", "I have a sore throat"], answerIndex: 0 },
            { id: "u10l3e3", kind: "match", pairs: [{ left: "il dolore", right: "the pain" }, { left: "la febbre", right: "the fever" }, { left: "il raffreddore", right: "the common cold" }, { left: "la tosse", right: "the cough" }] },
            { id: "u10l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The common cold", "The doctor", "I have a headache", "The fever"], answerIndex: 2, speak: "Ho mal di testa" },
            { id: "u10l3e5", kind: "choice", direction: "en_to_it", prompt: "I have a sore throat", choices: ["Ho mal di testa", "il dolore", "la tosse", "Ho mal di gola"], answerIndex: 3 },
            { id: "u10l3e6", kind: "choice", direction: "it_to_en", prompt: "il raffreddore", choices: ["The common cold", "The medicine", "The cough", "The doctor"], answerIndex: 0 }
          ]
        },
        {
          id: "u10l4",
          title: "Healthy habits",
          exercises: [
            { id: "u10l4e1", kind: "choice", direction: "en_to_it", prompt: "sport", choices: ["lo sport", "la palestra", "la verdura", "l'energia"], answerIndex: 0 },
            { id: "u10l4e2", kind: "choice", direction: "it_to_en", prompt: "la palestra", choices: ["The fruit", "The gym", "Health", "To swim"], answerIndex: 1 },
            { id: "u10l4e3", kind: "match", pairs: [{ left: "nuotare", right: "to swim" }, { left: "la frutta", right: "the fruit" }, { left: "la verdura", right: "the vegetables" }, { left: "il riposo", right: "the rest" }] },
            { id: "u10l4e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The energy", "To swim", "The rest", "Health"], answerIndex: 3, speak: "La salute" },
            { id: "u10l4e5", kind: "choice", direction: "en_to_it", prompt: "the energy", choices: ["l'energia", "la palestra", "la verdura", "lo sport"], answerIndex: 0 },
            { id: "u10l4e6", kind: "choice", direction: "it_to_en", prompt: "la verdura", choices: ["The fruit", "The vegetables", "The energy", "To swim"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u11",
      title: "Work & School",
      lessons: [
        {
          id: "u11l1",
          title: "Jobs",
          exercises: [
            { id: "u11l1e1", kind: "choice", direction: "en_to_it", prompt: "the teacher", choices: ["l'ingegnere", "l'insegnante", "l'autista", "l'avvocato"], answerIndex: 1 },
            { id: "u11l1e2", kind: "choice", direction: "it_to_en", prompt: "il cuoco", choices: ["The nurse", "The teacher", "The cook", "The student"], answerIndex: 2 },
            { id: "u11l1e3", kind: "match", pairs: [{ left: "l'avvocato", right: "the lawyer" }, { left: "l'ingegnere", right: "the engineer" }, { left: "lo studente", right: "the student" }, { left: "l'infermiere", right: "the nurse" }] },
            { id: "u11l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The driver", "The cook", "The student", "The teacher"], answerIndex: 0, speak: "L'autista" },
            { id: "u11l1e5", kind: "choice", direction: "en_to_it", prompt: "the artist", choices: ["l'ingegnere", "l'artista", "l'autista", "l'avvocato"], answerIndex: 1 },
            { id: "u11l1e6", kind: "choice", direction: "it_to_en", prompt: "lo studente", choices: ["The driver", "The cook", "The student", "The nurse"], answerIndex: 2 }
          ]
        },
        {
          id: "u11l2",
          title: "School",
          exercises: [
            { id: "u11l2e1", kind: "choice", direction: "en_to_it", prompt: "the book", choices: ["l'esame", "la penna", "il libro", "la classe"], answerIndex: 2 },
            { id: "u11l2e2", kind: "choice", direction: "it_to_en", prompt: "la penna", choices: ["The pencil", "The lesson", "The homework", "The pen"], answerIndex: 3 },
            { id: "u11l2e3", kind: "match", pairs: [{ left: "il quaderno", right: "the notebook" }, { left: "la lezione", right: "the lesson" }, { left: "la classe", right: "the classroom" }, { left: "l'esame", right: "the exam" }] },
            { id: "u11l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The lesson", "The homework", "The pencil", "The notebook"], answerIndex: 1, speak: "I compiti" },
            { id: "u11l2e5", kind: "choice", direction: "en_to_it", prompt: "the pencil", choices: ["l'esame", "la penna", "la matita", "la classe"], answerIndex: 2 },
            { id: "u11l2e6", kind: "choice", direction: "it_to_en", prompt: "la classe", choices: ["The book", "The lesson", "The pencil", "The classroom"], answerIndex: 3 }
          ]
        },
        {
          id: "u11l3",
          title: "At work",
          exercises: [
            { id: "u11l3e1", kind: "choice", direction: "en_to_it", prompt: "the computer", choices: ["la pausa", "il collega", "il lavoro", "il computer"], answerIndex: 3 },
            { id: "u11l3e2", kind: "choice", direction: "it_to_en", prompt: "il telefono", choices: ["The phone", "The meeting", "The salary", "The computer"], answerIndex: 0 },
            { id: "u11l3e3", kind: "match", pairs: [{ left: "la riunione", right: "the meeting" }, { left: "il collega", right: "the colleague" }, { left: "il capo", right: "the boss" }, { left: "lo stipendio", right: "the salary" }] },
            { id: "u11l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["The salary", "The phone", "The job", "The boss"], answerIndex: 2, speak: "Il lavoro" },
            { id: "u11l3e5", kind: "choice", direction: "en_to_it", prompt: "the break", choices: ["il computer", "il collega", "il lavoro", "la pausa"], answerIndex: 3 },
            { id: "u11l3e6", kind: "choice", direction: "it_to_en", prompt: "il capo", choices: ["The boss", "The meeting", "The job", "The phone"], answerIndex: 0 }
          ]
        },
        {
          id: "u11l4",
          title: "Work phrases",
          exercises: [
            { id: "u11l4e1", kind: "choice", direction: "en_to_it", prompt: "What do you do for work?", choices: ["Che lavoro fai?", "Sono uno studente", "Finisco alle cinque", "Lavoro in un ufficio"], answerIndex: 0 },
            { id: "u11l4e2", kind: "choice", direction: "it_to_en", prompt: "Lavoro in un ufficio", choices: ["I have a meeting", "I work in an office", "Have a good day at work!", "I study Italian"], answerIndex: 1 },
            { id: "u11l4e3", kind: "match", pairs: [{ left: "sono uno studente", right: "i am a student" }, { left: "studio italiano", right: "i study italian" }, { left: "ho una riunione", right: "i have a meeting" }, { left: "finisco alle cinque", right: "i finish at five" }] },
            { id: "u11l4e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["What do you do for work?", "I study Italian", "Have a good day at work!", "I am late"], answerIndex: 3, speak: "Sono in ritardo" },
            { id: "u11l4e5", kind: "choice", direction: "en_to_it", prompt: "Have a good day at work!", choices: ["Buon lavoro!", "Sono uno studente", "Finisco alle cinque", "Lavoro in un ufficio"], answerIndex: 0 },
            { id: "u11l4e6", kind: "choice", direction: "it_to_en", prompt: "Ho una riunione", choices: ["I finish at five", "I have a meeting", "What do you do for work?", "I study Italian"], answerIndex: 1 }
          ]
        }
      ]
    },
    {
      id: "u12",
      title: "Conversation",
      lessons: [
        {
          id: "u12l1",
          title: "Question words",
          exercises: [
            { id: "u12l1e1", kind: "choice", direction: "en_to_it", prompt: "who", choices: ["perché", "chi", "quale", "quando"], answerIndex: 1 },
            { id: "u12l1e2", kind: "choice", direction: "it_to_en", prompt: "che cosa", choices: ["How much", "Where", "What", "How"], answerIndex: 2 },
            { id: "u12l1e3", kind: "match", pairs: [{ left: "dove", right: "where" }, { left: "quando", right: "when" }, { left: "perché", right: "why" }, { left: "come", right: "how" }] },
            { id: "u12l1e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["How much", "Where", "How", "What"], answerIndex: 0, speak: "Quanto" },
            { id: "u12l1e5", kind: "choice", direction: "en_to_it", prompt: "which", choices: ["perché", "quale", "chi", "quando"], answerIndex: 1 },
            { id: "u12l1e6", kind: "choice", direction: "it_to_en", prompt: "perché", choices: ["Which", "Where", "Why", "How much"], answerIndex: 2 }
          ]
        },
        {
          id: "u12l2",
          title: "Small talk",
          exercises: [
            { id: "u12l2e1", kind: "choice", direction: "en_to_it", prompt: "Do you speak English?", choices: ["Mi dispiace", "Puoi ripetere?", "Parli inglese?", "Che cosa significa?"], answerIndex: 2 },
            { id: "u12l2e2", kind: "choice", direction: "it_to_en", prompt: "Non capisco", choices: ["Do you speak English?", "How do you say this?", "I do not know", "I do not understand"], answerIndex: 3 },
            { id: "u12l2e3", kind: "match", pairs: [{ left: "puoi ripetere", right: "can you repeat" }, { left: "parla più piano", right: "speak more slowly" }, { left: "come si dice questo", right: "how do you say this" }, { left: "che cosa significa", right: "what does it mean" }] },
            { id: "u12l2e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["How do you say this?", "I am sorry", "Do you speak English?", "Speak more slowly"], answerIndex: 1, speak: "Mi dispiace" },
            { id: "u12l2e5", kind: "choice", direction: "en_to_it", prompt: "I do not know", choices: ["Mi dispiace", "Puoi ripetere?", "Non lo so", "Che cosa significa?"], answerIndex: 2 },
            { id: "u12l2e6", kind: "choice", direction: "it_to_en", prompt: "Come si dice questo?", choices: ["I do not understand", "What does it mean?", "Do you speak English?", "How do you say this?"], answerIndex: 3 }
          ]
        },
        {
          id: "u12l3",
          title: "Opinions",
          exercises: [
            { id: "u12l3e1", kind: "choice", direction: "en_to_it", prompt: "I like it", choices: ["Non mi piace", "Certo", "È facile", "Mi piace"], answerIndex: 3 },
            { id: "u12l3e2", kind: "choice", direction: "it_to_en", prompt: "Non mi piace", choices: ["I do not like it", "Maybe", "It is difficult", "I agree"], answerIndex: 0 },
            { id: "u12l3e3", kind: "match", pairs: [{ left: "sono d'accordo", right: "i agree" }, { left: "forse", right: "maybe" }, { left: "certo", right: "of course" }, { left: "è bellissimo", right: "it is beautiful" }] },
            { id: "u12l3e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["It is easy", "I agree", "It is difficult", "It is beautiful"], answerIndex: 2, speak: "È difficile" },
            { id: "u12l3e5", kind: "choice", direction: "en_to_it", prompt: "It is easy", choices: ["Non mi piace", "Certo", "Mi piace", "È facile"], answerIndex: 3 },
            { id: "u12l3e6", kind: "choice", direction: "it_to_en", prompt: "Certo", choices: ["Of course", "Maybe", "It is easy", "I agree"], answerIndex: 0 }
          ]
        },
        {
          id: "u12l4",
          title: "Wishes",
          exercises: [
            { id: "u12l4e1", kind: "choice", direction: "en_to_it", prompt: "Have a good trip", choices: ["Buon viaggio", "Buon compleanno", "A presto", "Buona fortuna"], answerIndex: 0 },
            { id: "u12l4e2", kind: "choice", direction: "it_to_en", prompt: "Buon appetito", choices: ["See you tomorrow", "Enjoy your meal", "Have a good trip", "Good night"], answerIndex: 1 },
            { id: "u12l4e3", kind: "match", pairs: [{ left: "buona fortuna", right: "good luck" }, { left: "buon compleanno", right: "happy birthday" }, { left: "buona notte", right: "good night" }, { left: "a domani", right: "see you tomorrow" }] },
            { id: "u12l4e4", kind: "choice", direction: "listen", prompt: "Tap what you hear", choices: ["Enjoy your meal", "Good night", "Have a good trip", "See you soon"], answerIndex: 3, speak: "A presto" },
            { id: "u12l4e5", kind: "choice", direction: "en_to_it", prompt: "Best wishes", choices: ["Auguri", "Buon compleanno", "A presto", "Buona fortuna"], answerIndex: 0 },
            { id: "u12l4e6", kind: "choice", direction: "it_to_en", prompt: "Buona notte", choices: ["See you soon", "Good night", "Enjoy your meal", "See you tomorrow"], answerIndex: 1 }
          ]
        }
      ]
    }
  ]
};

package com.daytoday.data.database

import com.daytoday.model.Exercise

/**
 * Starter exercise catalogue bundled with the app. The workout flow reads exercises from Room
 * only, so the list ships with the binary instead of being fetched from the backend.
 */
object ExerciseSeed {

    fun defaultExercises(): List<Exercise> = listOf(
        exercise(
            id = "ex_pull_up",
            name = "Pull-Up",
            muscleGroup = "Back",
            equipment = "Bodyweight",
            instructions = "Hang from a bar with an overhand grip and pull your chin to the bar, controlling the descent."
        ),
        exercise(
            id = "ex_push_up",
            name = "Push-Up",
            muscleGroup = "Chest",
            equipment = "Bodyweight",
            instructions = "Keep a straight line from head to heels and lower your chest to the floor, then press back up."
        ),
        exercise(
            id = "ex_air_squat",
            name = "Air Squat",
            muscleGroup = "Legs",
            equipment = "Bodyweight",
            instructions = "Stand with feet shoulder width, sit the hips back and down until thighs are parallel, then drive through the feet."
        ),
        exercise(
            id = "ex_dips",
            name = "Dips",
            muscleGroup = "Chest",
            equipment = "Bodyweight",
            instructions = "Support yourself on parallel bars, lower until shoulders are below the elbows, then press back to the top."
        ),
        exercise(
            id = "ex_crunch",
            name = "Crunch",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Lie on your back with knees bent and curl the shoulders off the floor without pulling on the neck."
        ),
        exercise(
            id = "ex_plank",
            name = "Plank",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Brace the abdomen and hold a straight line from shoulders to heels for the prescribed time."
        ),
        exercise(
            id = "ex_lunge",
            name = "Lunge",
            muscleGroup = "Legs",
            equipment = "Bodyweight",
            instructions = "Step forward and lower the back knee toward the floor, then push through the front foot to stand."
        ),
        exercise(
            id = "ex_burpee",
            name = "Burpee",
            muscleGroup = "Full Body",
            equipment = "Bodyweight",
            instructions = "Squat down, place hands on the floor, jump the feet back to a plank, then jump up with an arm swing."
        ),
        exercise(
            id = "ex_bench_press",
            name = "Bench Press",
            muscleGroup = "Chest",
            equipment = "Barbell",
            instructions = "Lower the bar to mid-chest with elbows tucked, then press up and slightly back toward the lockout."
        ),
        exercise(
            id = "ex_back_squat",
            name = "Back Squat",
            muscleGroup = "Legs",
            equipment = "Barbell",
            instructions = "Brace the core, sit down between the hips until depth is reached, then drive up through the whole foot."
        ),
        exercise(
            id = "ex_deadlift",
            name = "Deadlift",
            muscleGroup = "Back",
            equipment = "Barbell",
            instructions = "Pull the slack from the floor with a flat back and push the floor away, finishing tall with the bar at the thighs."
        ),
        exercise(
            id = "ex_overhead_press",
            name = "Overhead Press",
            muscleGroup = "Shoulders",
            equipment = "Barbell",
            instructions = "Brace and squeeze the glutes, then press the bar from the shoulders to full overhead lockout."
        ),
        exercise(
            id = "ex_barbell_row",
            name = "Barbell Row",
            muscleGroup = "Back",
            equipment = "Barbell",
            instructions = "With a hip hinge and flat back, row the bar to the lower ribs and squeeze the shoulder blades."
        ),
        exercise(
            id = "ex_lat_pulldown",
            name = "Lat Pulldown",
            muscleGroup = "Back",
            equipment = "Cable Machine",
            instructions = "Pull the bar to the collarbone while driving the elbows down, then let the arms lengthen back up."
        ),
        exercise(
            id = "ex_bicep_curl",
            name = "Bicep Curl",
            muscleGroup = "Arms",
            equipment = "Dumbbell",
            instructions = "With elbows pinned to the sides, curl the weights to the shoulders and lower under control."
        ),
        exercise(
            id = "ex_triceps_pushdown",
            name = "Triceps Pushdown",
            muscleGroup = "Arms",
            equipment = "Cable Machine",
            instructions = "Pin the elbows to the sides and press the bar down until the arms are straight, then return slowly."
        ),
        exercise(
            id = "ex_leg_press",
            name = "Leg Press",
            muscleGroup = "Legs",
            equipment = "Machine",
            instructions = "Lower the sled to a comfortable depth without the lower back rounding off the pad, then press back up."
        ),
        exercise(
            id = "ex_leg_curl",
            name = "Leg Curl",
            muscleGroup = "Legs",
            equipment = "Machine",
            instructions = "Curl the pad toward the glutes and squeeze at the top, then extend slowly to the start."
        ),
        exercise(
            id = "ex_calf_raise",
            name = "Calf Raise",
            muscleGroup = "Legs",
            equipment = "Machine",
            instructions = "Rise onto the balls of the feet as high as possible, pause, then lower through a full range."
        ),
        exercise(
            id = "ex_cable_fly",
            name = "Cable Fly",
            muscleGroup = "Chest",
            equipment = "Cable Machine",
            instructions = "With soft elbows held at a fixed angle, sweep the arms together in front and control the return."
        ),
        exercise(
            id = "ex_lateral_raise",
            name = "Dumbbell Lateral Raise",
            muscleGroup = "Shoulders",
            equipment = "Dumbbell",
            instructions = "Raise the dumbbells out to the sides to shoulder height, leading with the elbows, then lower slowly."
        ),
        exercise(
            id = "ex_back_extension",
            name = "Back Extension",
            muscleGroup = "Lower Back",
            equipment = "Bodyweight",
            instructions = "With hips hinged, lift the torso to a straight line and squeeze the glutes without overextending the lower back."
        ),

        // ---- CHEST ----
        exercise(
            id = "ex_incline_db_press",
            name = "Incline Dumbbell Press",
            muscleGroup = "Chest",
            equipment = "Dumbbell",
            instructions = "Set the bench to a 30-45 degree incline and press the dumbbells up and slightly inward, keeping the shoulder blades pinned down."
        ),
        exercise(
            id = "ex_decline_push_up",
            name = "Decline Push-Up",
            muscleGroup = "Chest",
            equipment = "Bodyweight",
            instructions = "Elevate the feet on a bench and lower the chest toward the floor for a deeper stretch through the shoulders before pressing away."
        ),
        exercise(
            id = "ex_close_grip_push_up",
            name = "Close-Grip Push-Up",
            muscleGroup = "Chest",
            equipment = "Bodyweight",
            instructions = "Place the hands shoulder width apart and lower the chest between them, keeping the elbows tight to the ribs."
        ),
        exercise(
            id = "ex_dumbbell_fly",
            name = "Dumbbell Fly",
            muscleGroup = "Chest",
            equipment = "Dumbbell",
            instructions = "With a soft elbow angle held fixed, sweep the dumbbells together over the chest and control the wide return."
        ),
        exercise(
            id = "ex_bench_dip",
            name = "Bench Dip",
            muscleGroup = "Chest",
            equipment = "Bodyweight",
            instructions = "Hands on a bench behind you, bend the elbows straight back and lower until the upper arms are parallel to the floor."
        ),
        exercise(
            id = "ex_close_grip_bench_press",
            name = "Close-Grip Bench Press",
            muscleGroup = "Chest",
            equipment = "Barbell",
            instructions = "Use a grip narrower than shoulder width to shift the work to the triceps, keeping the elbows tucked through the press."
        ),
        exercise(
            id = "ex_diamond_push_up",
            name = "Diamond Push-Up",
            muscleGroup = "Chest",
            equipment = "Bodyweight",
            instructions = "Form a diamond with the hands under the chest and lower until the triceps touch the floor, then press back up."
        ),
        exercise(
            id = "ex_push_up_decline_hands",
            name = "Knee Push-Up",
            muscleGroup = "Chest",
            equipment = "Bodyweight",
            instructions = "Drop to the knees for a supported push-up that keeps the hips extended and the glutes engaged."
        ),

        // ---- BACK ----
        exercise(
            id = "ex_chin_up",
            name = "Chin-Up",
            muscleGroup = "Back",
            equipment = "Bodyweight",
            instructions = "Use an underhand grip and pull the chin to the bar with the elbows driving down, then lower to a full hang."
        ),
        exercise(
            id = "ex_pendlay_row",
            name = "Pendlay Row",
            muscleGroup = "Back",
            equipment = "Barbell",
            instructions = "Brace the hinge and pull the bar to the lower ribs, then lower it to full extension without letting the torso rise."
        ),
        exercise(
            id = "ex_seated_cable_row",
            name = "Seated Cable Row",
            muscleGroup = "Back",
            equipment = "Cable Machine",
            instructions = "Sit tall with the chest up, pull the handle to the navel, and let the arms lengthen forward under control."
        ),
        exercise(
            id = "ex_t_bar_row",
            name = "T-Bar Row",
            muscleGroup = "Back",
            equipment = "Barbell",
            instructions = "Load the bar and hinge to a 45 degree torso, then row it to the chest with elbows tracking close to the body."
        ),
        exercise(
            id = "ex_renegade_row",
            name = "Renegade Row",
            muscleGroup = "Back",
            equipment = "Dumbbell",
            instructions = "From a wide plank, row alternating dumbbells while resisting torso rotation, then step the feet back under control."
        ),
        exercise(
            id = "ex_face_pull",
            name = "Face Pull",
            muscleGroup = "Back",
            equipment = "Cable Machine",
            instructions = "Pull the rope toward the forehead with the elbows high to work the rear delts and upper back."
        ),
        exercise(
            id = "ex_barbell_shrug",
            name = "Barbell Shrug",
            muscleGroup = "Back",
            equipment = "Barbell",
            instructions = "Stand tall and shrug the shoulders straight up as high as they travel without bending the knees or rounding the back."
        ),
        exercise(
            id = "ex_single_arm_db_row",
            name = "Single-Arm Dumbbell Row",
            muscleGroup = "Back",
            equipment = "Dumbbell",
            instructions = "Support one hand on a bench and row the working arm to the hip, pausing to squeeze the shoulder blade."
        ),
        exercise(
            id = "ex_romanian_deadlift",
            name = "Romanian Deadlift",
            muscleGroup = "Back",
            equipment = "Barbell",
            instructions = "With a soft knee and a flat back, push the hips back and lower the bar to mid-shin, then drive the hips forward."
        ),
        exercise(
            id = "ex_band_pull_apart",
            name = "Band Pull-Apart",
            muscleGroup = "Back",
            equipment = "Band",
            instructions = "Hold the band at shoulder height and pull it apart in front of the chest to open the shoulders and upper back."
        ),

        // ---- LEGS ----
        exercise(
            id = "ex_bulgarian_split_squat",
            name = "Bulgarian Split Squat",
            muscleGroup = "Legs",
            equipment = "Bodyweight",
            instructions = "Rest the rear foot on a bench, drop straight down through the front leg, and drive up through the heel."
        ),
        exercise(
            id = "ex_walking_lunge",
            name = "Walking Lunge",
            muscleGroup = "Legs",
            equipment = "Bodyweight",
            instructions = "Step forward into a long stride, lower the back knee toward the floor, and push through the front foot to continue."
        ),
        exercise(
            id = "ex_step_up",
            name = "Step-Up",
            muscleGroup = "Legs",
            equipment = "Dumbbell",
            instructions = "Place the lead foot flat on a box, stand up without leaning forward, and lower with control."
        ),
        exercise(
            id = "ex_leg_extension",
            name = "Leg Extension",
            muscleGroup = "Legs",
            equipment = "Machine",
            instructions = "Align the knee with the machine pivot and extend the knees until the legs are straight, then lower slowly."
        ),
        exercise(
            id = "ex_standing_calf_raise",
            name = "Standing Calf Raise",
            muscleGroup = "Legs",
            equipment = "Machine",
            instructions = "Push through the balls of the feet to the highest heels point, squeeze, and lower through a full range."
        ),
        exercise(
            id = "ex_wall_sit",
            name = "Wall Sit",
            muscleGroup = "Legs",
            equipment = "Bodyweight",
            instructions = "Slide the back down a wall until the knees sit at ninety degrees and hold the position with the core braced."
        ),
        exercise(
            id = "ex_single_leg_rdl",
            name = "Single-Leg Romanian Deadlift",
            muscleGroup = "Legs",
            equipment = "Dumbbell",
            instructions = "Balance on one leg with a soft knee and hinge until the torso and rear leg reach a straight line, then stand tall."
        ),
        exercise(
            id = "ex_sissy_squat",
            name = "Sissy Squat",
            muscleGroup = "Legs",
            equipment = "Bodyweight",
            instructions = "Hold the heels off the floor, sit the hips back and down, and extend the knees to stand back up on the toes."
        ),

        // ---- GLUTES ----
        exercise(
            id = "ex_glute_bridge",
            name = "Glute Bridge",
            muscleGroup = "Glutes",
            equipment = "Bodyweight",
            instructions = "Lie on the back with the knees bent, drive through the heels to lift the hips until the body forms a straight line."
        ),
        exercise(
            id = "ex_hip_thrust",
            name = "Hip Thrust",
            muscleGroup = "Glutes",
            equipment = "Barbell",
            instructions = "Rest the shoulder blades on a bench and drive the hips to full lockout, squeezing the glutes at the top."
        ),
        exercise(
            id = "ex_fire_hydrant",
            name = "Fire Hydrant",
            muscleGroup = "Glutes",
            equipment = "Bodyweight",
            instructions = "From all fours, lift one knee out to the side while keeping the hips level, then return under control."
        ),
        exercise(
            id = "ex_kettlebell_swing",
            name = "Kettlebell Swing",
            muscleGroup = "Glutes",
            equipment = "Kettlebell",
            instructions = "Hinge and snap the hips forward to float the bell to chest height, letting the arms stay loose."
        ),
        exercise(
            id = "ex_kettlebell_hip_thrust",
            name = "Kettlebell Hip Thrust",
            muscleGroup = "Glutes",
            equipment = "Kettlebell",
            instructions = "Sit the bell on the hip crease and finish each rep by fully extending the hips and squeezing the glutes."
        ),
        exercise(
            id = "ex_glute_bridge_single_leg",
            name = "Single-Leg Glute Bridge",
            muscleGroup = "Glutes",
            equipment = "Bodyweight",
            instructions = "From a bridge position, extend one leg and hold the hips level before lowering and repeating on the other side."
        ),

        // ---- SHOULDERS ----
        exercise(
            id = "ex_dumbbell_shoulder_press",
            name = "Dumbbell Shoulder Press",
            muscleGroup = "Shoulders",
            equipment = "Dumbbell",
            instructions = "Brace the glutes, keep the ribs down, and press the dumbbells overhead until the arms lock out."
        ),
        exercise(
            id = "ex_arnold_press",
            name = "Arnold Press",
            muscleGroup = "Shoulders",
            equipment = "Dumbbell",
            instructions = "Start with the palms facing in, rotate the thumbs up as the elbows lead the way, then reverse on the way down."
        ),
        exercise(
            id = "ex_dumbbell_front_raise",
            name = "Dumbbell Front Raise",
            muscleGroup = "Shoulders",
            equipment = "Dumbbell",
            instructions = "Raise a dumbbell straight in front to shoulder height with a soft elbow, then lower slowly."
        ),
        exercise(
            id = "ex_rear_delt_fly",
            name = "Rear Delt Fly",
            muscleGroup = "Shoulders",
            equipment = "Dumbbell",
            instructions = "Hinge forward slightly and raise the dumbbells out to the sides to target the rear deltoids."
        ),
        exercise(
            id = "ex_upright_row",
            name = "Upright Row",
            muscleGroup = "Shoulders",
            equipment = "Barbell",
            instructions = "Pull the bar to the top of the sternum with the elbows leading and the torso staying tall."
        ),
        exercise(
            id = "ex_pike_push_up",
            name = "Pike Push-Up",
            muscleGroup = "Shoulders",
            equipment = "Bodyweight",
            instructions = "Hike the hips high, lower the crown of the head toward the floor, and press up to a strong overhead lockout."
        ),
        exercise(
            id = "ex_landmine_press",
            name = "Landmine Press",
            muscleGroup = "Shoulders",
            equipment = "Barbell",
            instructions = "From a staggered stance, press the angled bar upward with the arms and finish with the biceps by the ear."
        ),

        // ---- ARMS ----
        exercise(
            id = "ex_hammer_curl",
            name = "Hammer Curl",
            muscleGroup = "Arms",
            equipment = "Dumbbell",
            instructions = "Keep a neutral grip and curl without rotating the wrists to work the biceps brachii and forearms."
        ),
        exercise(
            id = "ex_preacher_curl",
            name = "Preacher Curl",
            muscleGroup = "Arms",
            equipment = "Dumbbell",
            instructions = "Rest the upper arms on the pad and curl until the forearms are vertical, keeping the elbows anchored."
        ),
        exercise(
            id = "ex_skull_crushers",
            name = "Skull Crushers",
            muscleGroup = "Arms",
            equipment = "Barbell",
            instructions = "With the elbows fixed just in front of the head, lower the bar behind the crown and extend the arms back up."
        ),
        exercise(
            id = "ex_overhead_tricep_ext",
            name = "Overhead Tricep Extension",
            muscleGroup = "Arms",
            equipment = "Cable Machine",
            instructions = "Keep the elbows pointing forward and extend the arms until fully straight, then lower to a full bend."
        ),
        exercise(
            id = "ex_zottman_curl",
            name = "Zottman Curl",
            muscleGroup = "Arms",
            equipment = "Dumbbell",
            instructions = "Curl with the palms up then rotate to face down on the way down to hit both the biceps and the triceps."
        ),
        exercise(
            id = "ex_underhand_row",
            name = "Underhand Row",
            muscleGroup = "Arms",
            equipment = "Dumbbell",
            instructions = "With an underhand grip and a chest-supported torso, row the dumbbells toward the lower ribs."
        ),

        // ---- CORE ----
        exercise(
            id = "ex_side_plank",
            name = "Side Plank",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Stack the feet and lift the hips so the body forms a straight line from head to heels, bracing the shoulder."
        ),
        exercise(
            id = "ex_bicycle_crunch",
            name = "Bicycle Crunch",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Rotate the spine toward one bent knee at a time while the opposite leg extends and the shoulders stay lifted."
        ),
        exercise(
            id = "ex_hollow_body_hold",
            name = "Hollow Body Hold",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Press the lower back into the floor, lift the shoulders and legs, and hold the banana shape without arching."
        ),
        exercise(
            id = "ex_lying_leg_raise",
            name = "Lying Leg Raise",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Lie flat and raise both legs to just above hip height, then lower them back without letting the lower back lift."
        ),
        exercise(
            id = "ex_russian_twist",
            name = "Russian Twist",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Sit balanced with the knees bent and rotate the torso side to side while passing the weight across the midline."
        ),
        exercise(
            id = "ex_dead_bug",
            name = "Dead Bug",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Lie on the back with the arms and legs raised, then extend the opposite limbs while keeping the lower back pressed down."
        ),
        exercise(
            id = "ex_mountain_climber",
            name = "Mountain Climber",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "From a plank, drive the knees toward the chest in quick succession while the shoulders stay stacked over the hands."
        ),
        exercise(
            id = "ex_cable_crunch",
            name = "Cable Crunch",
            muscleGroup = "Core",
            equipment = "Cable Machine",
            instructions = "Kneel and pull the rope down toward the floor, rounding the spine and keeping the hips fixed."
        ),
        exercise(
            id = "ex_side_bend",
            name = "Side Bend",
            muscleGroup = "Core",
            equipment = "Dumbbell",
            instructions = "Hold one dumbbell overhead and bend sideways to lengthen the same-side obliques, then return tall."
        ),

        // ---- CARDIO ----
        exercise(
            id = "ex_jumping_jack",
            name = "Jumping Jack",
            muscleGroup = "Cardio",
            equipment = "Bodyweight",
            instructions = "Jump the feet wide while sweeping the arms overhead, then return to the tall stance under control."
        ),
        exercise(
            id = "ex_skater_jump",
            name = "Skater Jump",
            muscleGroup = "Cardio",
            equipment = "Bodyweight",
            instructions = "Bound sideways onto one foot and drive the opposite knee across, then push off and switch sides."
        ),
        exercise(
            id = "ex_box_jump",
            name = "Box Jump",
            muscleGroup = "Cardio",
            equipment = "Bodyweight",
            instructions = "Load the legs, drive the arms back, and jump onto the box landing softly on the box in a quarter squat."
        ),
        exercise(
            id = "ex_high_knees",
            name = "High Knees",
            muscleGroup = "Cardio",
            equipment = "Bodyweight",
            instructions = "Run in place driving the knees above hip height with short quick steps and an upright posture."
        ),
        exercise(
            id = "ex_buttle_jump",
            name = "Butt Kicks",
            muscleGroup = "Cardio",
            equipment = "Bodyweight",
            instructions = "Run in place alternating heel flicks toward the glutes while keeping the knees driving forward."
        ),
        exercise(
            id = "ex_rowing_machine",
            name = "Rowing Machine",
            muscleGroup = "Cardio",
            equipment = "Machine",
            instructions = "Drive with the legs first, swing the torso open, then reverse the sequence to recover and repeat."
        ),

        // ---- FULL BODY ----
        exercise(
            id = "ex_bear_crawl",
            name = "Bear Crawl",
            muscleGroup = "Full Body",
            equipment = "Bodyweight",
            instructions = "Hover the knees just off the floor and crawl forward and back keeping the hips low and the back flat."
        ),
        exercise(
            id = "ex_jump_squat",
            name = "Jump Squat",
            muscleGroup = "Full Body",
            equipment = "Bodyweight",
            instructions = "Sit down to depth, explode up off the floor and land softly straight into the next repetition."
        ),
        exercise(
            id = "ex_kettlebell_clean",
            name = "Kettlebell Clean",
            muscleGroup = "Full Body",
            equipment = "Kettlebell",
            instructions = "Swing the bell to hip height, dip the hips, then pull it close to the shoulder as the body stands tall."
        ),
        exercise(
            id = "ex_man_maker",
            name = "Man Maker",
            muscleGroup = "Full Body",
            equipment = "Dumbbell",
            instructions = "Bend at the hips and knees, then stand up sweeping the dumbbells past the shins to shoulder height."
        ),
        exercise(
            id = "ex_devils_press",
            name = "Devil's Press",
            muscleGroup = "Full Body",
            equipment = "Dumbbell",
            instructions = "Open the dumbbells on the floor, hike into a plank, and press them overhead as the hips rise into a pike."
        ),
        exercise(
            id = "ex_turkey_curl",
            name = "Turkey Goblet Squat",
            muscleGroup = "Full Body",
            equipment = "Dumbbell",
            instructions = "Hold one dumbbell at the chest and squat between the feet, then stand up and keep the weight on the heels."
        ),

        exercise(
            id = "ex_floor_press",
            name = "Floor Press",
            muscleGroup = "Chest",
            equipment = "Barbell",
            instructions = "Lie on the floor with the bar over the chest, lower until the triceps touch, then press back to lockout."
        ),
        exercise(
            id = "ex_incline_cable_fly",
            name = "Incline Cable Fly",
            muscleGroup = "Chest",
            equipment = "Cable Machine",
            instructions = "Set the bench to a low incline between the cables and sweep the handles together over the chest, controlling the stretch."
        ),
        exercise(
            id = "ex_wide_grip_push_up",
            name = "Wide-Grip Push-Up",
            muscleGroup = "Chest",
            equipment = "Bodyweight",
            instructions = "Place the hands well outside the shoulders and lower the chest between them, keeping the elbows flared and the body rigid."
        ),
        exercise(
            id = "ex_dumbbell_pullover",
            name = "Dumbbell Pullover",
            muscleGroup = "Chest",
            equipment = "Dumbbell",
            instructions = "Lie across a bench or on the floor, lower the dumbbell behind the head with soft elbows, then pull it back over the chest."
        ),
        exercise(
            id = "ex_chest_supported_row",
            name = "Chest-Supported Row",
            muscleGroup = "Back",
            equipment = "Dumbbell",
            instructions = "Lie face down on an incline bench and row both dumbbells to the ribs, pausing to squeeze the shoulder blades together."
        ),
        exercise(
            id = "ex_straight_arm_pulldown",
            name = "Straight-Arm Pulldown",
            muscleGroup = "Back",
            equipment = "Cable Machine",
            instructions = "Face the stack with straight arms and sweep the bar down to the thighs, keeping the elbows locked and the lats engaged."
        ),
        exercise(
            id = "ex_meadows_row",
            name = "Meadows Row",
            muscleGroup = "Back",
            equipment = "Dumbbell",
            instructions = "Hinge with one hand supported and row the dumbbell high with the elbow flaring out, lowering slowly to a full stretch."
        ),
        exercise(
            id = "ex_dead_hang",
            name = "Dead Hang",
            muscleGroup = "Back",
            equipment = "Bodyweight",
            instructions = "Grip the bar with the arms fully extended and hold the body still, letting the shoulders stay active rather than slacking."
        ),
        exercise(
            id = "ex_single_arm_pulldown",
            name = "Single-Arm Lat Pulldown",
            muscleGroup = "Back",
            equipment = "Cable Machine",
            instructions = "Kneel or sit and pull one handle to the shoulder blade while keeping the torso square, then control the weight back up."
        ),
        exercise(
            id = "ex_hack_squat",
            name = "Hack Squat",
            muscleGroup = "Legs",
            equipment = "Machine",
            instructions = "Set the back flat on the pad, lower until the knees reach ninety degrees, then drive through the whole foot."
        ),
        exercise(
            id = "ex_nordic_curl",
            name = "Nordic Hamstring Curl",
            muscleGroup = "Legs",
            equipment = "Bodyweight",
            instructions = "Kneel with the ankles anchored and lower the torso forward as slowly as possible, then use the hamstrings to pull back up."
        ),
        exercise(
            id = "ex_goblet_squat",
            name = "Goblet Squat",
            muscleGroup = "Legs",
            equipment = "Dumbbell",
            instructions = "Hold the dumbbell vertically at the chest, squat deep between the feet with the elbows inside the knees, then stand tall."
        ),
        exercise(
            id = "ex_lateral_lunge",
            name = "Lateral Lunge",
            muscleGroup = "Legs",
            equipment = "Bodyweight",
            instructions = "Step wide to one side, sit the hips back over that heel with the other leg straight, then push back to standing."
        ),
        exercise(
            id = "ex_seated_calf_raise",
            name = "Seated Calf Raise",
            muscleGroup = "Legs",
            equipment = "Machine",
            instructions = "Sit with the knees bent and the pads on the thighs, raise the heels as high as possible, then lower for a full stretch."
        ),
        exercise(
            id = "ex_cable_kickback",
            name = "Cable Kickback",
            muscleGroup = "Glutes",
            equipment = "Cable Machine",
            instructions = "With a low cuff on the ankle, drive one leg back and squeeze the glute at full extension, then return under control."
        ),
        exercise(
            id = "ex_clamshell",
            name = "Clamshell",
            muscleGroup = "Glutes",
            equipment = "Band",
            instructions = "Lie on the side with the knees bent and the band above the knees, then open the top knee while keeping the feet together."
        ),
        exercise(
            id = "ex_donkey_kick",
            name = "Donkey Kick",
            muscleGroup = "Glutes",
            equipment = "Bodyweight",
            instructions = "From all fours, drive one heel toward the ceiling with the knee bent, squeezing the glute at the top before lowering."
        ),
        exercise(
            id = "ex_single_leg_hip_thrust",
            name = "Single-Leg Hip Thrust",
            muscleGroup = "Glutes",
            equipment = "Dumbbell",
            instructions = "Rest the upper back on a bench, place one foot flat, and drive the hips to lockout while the free leg stays extended."
        ),
        exercise(
            id = "ex_cable_lateral_raise",
            name = "Cable Lateral Raise",
            muscleGroup = "Shoulders",
            equipment = "Cable Machine",
            instructions = "Stand beside the low pulley and raise the arm out to the side to shoulder height, leading with the elbow and lowering slowly."
        ),
        exercise(
            id = "ex_machine_shoulder_press",
            name = "Machine Shoulder Press",
            muscleGroup = "Shoulders",
            equipment = "Machine",
            instructions = "Adjust the seat so the handles sit at shoulder height, press until the arms lock out, then return with control."
        ),
        exercise(
            id = "ex_rear_delt_machine",
            name = "Rear Delt Machine",
            muscleGroup = "Shoulders",
            equipment = "Machine",
            instructions = "Set the chest against the pad, push the handles outward with soft elbows, and pause as the rear delts contract."
        ),
        exercise(
            id = "ex_scaption_raise",
            name = "Scaption Raise",
            muscleGroup = "Shoulders",
            equipment = "Dumbbell",
            instructions = "Raise the dumbbells in a V shape slightly in front of the body to shoulder height, then lower them under control."
        ),
        exercise(
            id = "ex_cable_front_raise",
            name = "Cable Front Raise",
            muscleGroup = "Shoulders",
            equipment = "Cable Machine",
            instructions = "Face the low pulley and raise the handle straight in front to eye level, keeping the torso still and the movement smooth."
        ),
        exercise(
            id = "ex_concentration_curl",
            name = "Concentration Curl",
            muscleGroup = "Arms",
            equipment = "Dumbbell",
            instructions = "Sit with the elbow braced against the inner thigh and curl the dumbbell to the shoulder, lowering slowly without swinging."
        ),
        exercise(
            id = "ex_cable_hammer_curl",
            name = "Cable Hammer Curl",
            muscleGroup = "Arms",
            equipment = "Cable Machine",
            instructions = "Hold the rope with a neutral grip and curl toward the shoulders while keeping the elbows pinned to the sides."
        ),
        exercise(
            id = "ex_tricep_kickback",
            name = "Tricep Kickback",
            muscleGroup = "Arms",
            equipment = "Dumbbell",
            instructions = "Hinge forward with the upper arm parallel to the floor, extend the elbow until the arm is straight, then return slowly."
        ),
        exercise(
            id = "ex_reverse_curl",
            name = "Reverse Curl",
            muscleGroup = "Arms",
            equipment = "Barbell",
            instructions = "Grip the bar with the palms facing down and curl to shoulder height, keeping the wrists neutral throughout the movement."
        ),
        exercise(
            id = "ex_spider_curl",
            name = "Spider Curl",
            muscleGroup = "Arms",
            equipment = "Barbell",
            instructions = "Lie chest down on a steep incline bench, curl the bar to the shoulders, and lower it to full arm extension."
        ),
        exercise(
            id = "ex_ab_wheel_rollout",
            name = "Ab Wheel Rollout",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Kneel with the wheel under the shoulders, roll forward until the hips extend, then pull back using the abdominals."
        ),
        exercise(
            id = "ex_hanging_leg_raise",
            name = "Hanging Leg Raise",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Hang from the bar with straight legs and raise them to hip height or above, lowering slowly without swinging."
        ),
        exercise(
            id = "ex_cable_woodchop",
            name = "Cable Woodchop",
            muscleGroup = "Core",
            equipment = "Cable Machine",
            instructions = "Set the pulley high, pull the handle diagonally across the body with straight arms, and rotate through the trunk under control."
        ),
        exercise(
            id = "ex_v_up",
            name = "V-Up",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "Lie flat with the arms overhead, then lift the legs and torso together so the hands meet the shins at the top."
        ),
        exercise(
            id = "ex_bird_dog",
            name = "Bird Dog",
            muscleGroup = "Core",
            equipment = "Bodyweight",
            instructions = "From all fours, extend the opposite arm and leg until both are level with the torso, pause, then return with control."
        ),
        exercise(
            id = "ex_jump_rope",
            name = "Jump Rope",
            muscleGroup = "Cardio",
            equipment = "Bodyweight",
            instructions = "Spin the rope from the wrists and spring over it on the balls of the feet with soft, quick bounces."
        ),
        exercise(
            id = "ex_sprint",
            name = "Sprint",
            muscleGroup = "Cardio",
            equipment = "Bodyweight",
            instructions = "Drive the knees and arms hard for a short maximal effort, staying tall and landing lightly under the hips."
        ),
        exercise(
            id = "ex_plyo_lunge",
            name = "Plyo Lunge",
            muscleGroup = "Cardio",
            equipment = "Bodyweight",
            instructions = "Jump explosively from a lunge and switch the legs in midair, landing softly with the front knee tracking over the foot."
        ),
        exercise(
            id = "ex_broad_jump",
            name = "Broad Jump",
            muscleGroup = "Cardio",
            equipment = "Bodyweight",
            instructions = "Load the hips, swing the arms, and jump forward as far as possible, landing softly in a quarter squat."
        ),
        exercise(
            id = "ex_air_bike",
            name = "Air Bike",
            muscleGroup = "Cardio",
            equipment = "Machine",
            instructions = "Push and pull the handles while pedaling hard, keeping the chest up and breathing steadily through the entire interval."
        ),
        exercise(
            id = "ex_snatch",
            name = "Dumbbell Snatch",
            muscleGroup = "Full Body",
            equipment = "Dumbbell",
            instructions = "Hinge with a flat back, pull the dumbbell from the floor and punch it overhead in one continuous motion."
        ),
        exercise(
            id = "ex_thruster",
            name = "Barbell Thruster",
            muscleGroup = "Full Body",
            equipment = "Barbell",
            instructions = "Squat to full depth with the bar at the shoulders, then drive up and press it overhead in one fluid repetition."
        ),
        exercise(
            id = "ex_turkish_get_up",
            name = "Turkish Get-Up",
            muscleGroup = "Full Body",
            equipment = "Kettlebell",
            instructions = "Lie down holding the bell overhead, then rise to standing through each stage while keeping the arm locked and eyes on the bell."
        ),
        exercise(
            id = "ex_farmer_walk",
            name = "Farmer Walk",
            muscleGroup = "Full Body",
            equipment = "Dumbbell",
            instructions = "Hold a heavy dumbbell at each side and walk with tall posture and braced core, setting the weights down gently."
        ),
        exercise(
            id = "ex_good_morning",
            name = "Good Morning",
            muscleGroup = "Lower Back",
            equipment = "Barbell",
            instructions = "Rest the bar across the upper back, hinge forward with soft knees until the torso is near parallel, then stand tall."
        ),
        exercise(
            id = "ex_superman",
            name = "Superman",
            muscleGroup = "Lower Back",
            equipment = "Bodyweight",
            instructions = "Lie face down and lift the arms, chest, and legs off the floor at the same time, then lower with control."
        ),
        exercise(
            id = "ex_weighted_back_extension",
            name = "Weighted Back Extension",
            muscleGroup = "Lower Back",
            equipment = "Dumbbell",
            instructions = "Hold a dumbbell at the chest, hinge over the pad, then extend the hips and squeeze the glutes to a straight line."
        ),
        exercise(
            id = "ex_pec_deck_fly",
            name = "Pec Deck Fly",
            muscleGroup = "Chest",
            equipment = "Machine",
            instructions = "Set the pads at shoulder height, keep the elbows softly bent, and bring the forearms together in front of the chest."
        ),
        exercise(
            id = "ex_seated_chest_press",
            name = "Seated Chest Press",
            muscleGroup = "Chest",
            equipment = "Machine",
            instructions = "Sit with the back flat against the pad and press the handles forward until the arms are straight, then return under control."
        ),
        exercise(
            id = "ex_standing_cable_chest_press",
            name = "Standing Cable Chest Press",
            muscleGroup = "Chest",
            equipment = "Cable Machine",
            instructions = "Stand in a staggered stance between the cables and press the handles forward at chest height while bracing the trunk."
        ),
        exercise(
            id = "ex_seated_overhead_press",
            name = "Seated Overhead Press",
            muscleGroup = "Shoulders",
            equipment = "Machine",
            instructions = "Sit with the back supported and press the handles from shoulder height to full lockout overhead, then lower slowly."
        ),
        exercise(
            id = "ex_twisted_cable_overhead_press",
            name = "Twisted Cable Overhead Press",
            muscleGroup = "Shoulders",
            equipment = "Cable Machine",
            instructions = "Set a cable at shoulder height, press the handle overhead while rotating the palm up, then lower with control."
        )
    )

    /**
     * Inserts any bundled exercises that are not in the table yet. Runs on first launch and again on
     * every read, so exercises added in later app versions appear without wiping user data or plans.
     */
    suspend fun seedMissingExercises(exerciseDao: ExerciseDao) {
        val existingIds = exerciseDao.getAllExercises().map { it.id }.toSet()
        val missing = defaultExercises()
            .filterNot { it.id in existingIds }
            .map { ExerciseEntity.fromModel(it) }
        if (missing.isNotEmpty()) exerciseDao.insertAll(missing)
    }

    private fun exercise(
        id: String,
        name: String,
        muscleGroup: String,
        equipment: String,
        instructions: String
    ): Exercise = Exercise(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        instructions = instructions,
        demoVideoUrl = null
    )
}
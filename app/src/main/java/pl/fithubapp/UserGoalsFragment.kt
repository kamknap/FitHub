package pl.fithubapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import pl.fithubapp.data.*
import pl.fithubapp.logic.UserCalculator
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs

class GoalsFragment : Fragment(R.layout.fragment_user_goals) {

    private lateinit var sbMainGoal: SeekBar
    private lateinit var tvTargetWeight: TextView
    private lateinit var spActivityLevel: Spinner
    private lateinit var spTrainingFrequency: Spinner
    private lateinit var cbNotifyMeals: CheckBox
    private lateinit var cbNotifyTraining: CheckBox
    private lateinit var cbNotifyWeighIn: CheckBox
    private lateinit var btnConfirmGoals: Button
    private lateinit var tvDetailedGoalLabel: TextView

    private var userData: OnboardingUserData? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let { bundle ->
            userData = OnboardingUserData(
                name = bundle.getString("userName", ""),
                sex = bundle.getString("userSex", ""),
                birthDate = bundle.getString("userBirthDate", ""),
                weight = bundle.getDouble("userWeight", 0.0),
                height = bundle.getDouble("userHeight", 0.0)
            )
        }

        initViews(view)
        setupFrequencySpinner()
        setupClickListeners()
        setupTextWatchers()
        setupActivitySpinner()

        sbMainGoal.progress = 1
        updateGoalUi(1)
    }

    private fun initViews(view: View) {
        sbMainGoal = view.findViewById(R.id.sbMainGoal)
        tvTargetWeight = view.findViewById(R.id.tvTargetWeight)
        spActivityLevel = view.findViewById(R.id.spActivityLevel)
        spTrainingFrequency = view.findViewById(R.id.spTrainingFrequency)
        cbNotifyMeals = view.findViewById(R.id.cbNotifyMeals)
        cbNotifyTraining = view.findViewById(R.id.cbNotifyTraining)
        cbNotifyWeighIn = view.findViewById(R.id.cbNotifyWeighIn)
        btnConfirmGoals = view.findViewById(R.id.btnConfirmGoals)
        tvDetailedGoalLabel = view.findViewById(R.id.tvDetailedGoalLabel)
    }

    private fun updateGoalUi(progress: Int) {
        when (progress) {
            0 -> {
                Toast.makeText(requireContext(), "Cel: Schudnąć", Toast.LENGTH_SHORT).show()
                tvDetailedGoalLabel.text = "Wybierz docelową wagę"
                tvTargetWeight.text = ""
                tvTargetWeight.hint = "Waga docelowa (kg)"
                tvTargetWeight.isClickable = true
                tvTargetWeight.isFocusable = true
                tvTargetWeight.setOnClickListener { showTargetWeightPicker("loose", userData?.weight) }
            }
            1 -> {
                Toast.makeText(requireContext(), "Cel: Utrzymać wagę", Toast.LENGTH_SHORT).show()
                userData?.weight?.let { currentWeight ->
                    tvDetailedGoalLabel.text = "Waga aktualna"
                    tvTargetWeight.text = currentWeight.toInt().toString()
                    tvTargetWeight.hint = ""
                    tvTargetWeight.isClickable = false
                    tvTargetWeight.isFocusable = false
                    tvTargetWeight.setOnClickListener(null)
                }
            }
            2 -> {
                Toast.makeText(requireContext(), "Cel: Przytyć", Toast.LENGTH_SHORT).show()
                tvDetailedGoalLabel.text = "Wybierz docelową wagę"
                tvTargetWeight.text = ""
                tvTargetWeight.hint = "Waga docelowa (kg)"
                tvTargetWeight.isClickable = true
                tvTargetWeight.isFocusable = true
                tvTargetWeight.setOnClickListener { showTargetWeightPicker("gain", userData?.weight) }
            }
        }
    }

    private fun setupFrequencySpinner() {
        val items = (0..7).map { if (it == 0) "0 dni/tydz." else "$it dni/tydz." }
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            items
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        spTrainingFrequency.adapter = adapter
        spTrainingFrequency.setSelection(3, false) // domyślnie 3 dni/tydz.
    }

    private fun setupClickListeners() {
        sbMainGoal.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateGoalUi(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnConfirmGoals.setOnClickListener {
            val goals = getGoalsData()
            if (!goals.isComplete()) {
                Toast.makeText(requireContext(), "Uzupełnij docelową wagę", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Walidacje pomocnicze (zakresy)
            if (!goals.isValidRanges()) {
                Toast.makeText(requireContext(), "Sprawdź zakresy pól (waga 30–300 kg, aktywność 1–5, trening 0–7).", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            saveGoals(goals)
        }
    }

    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        tvTargetWeight.addTextChangedListener(watcher)
    }

    private fun setupActivitySpinner() {
        val activityLevels = listOf(
            "Brak aktywności/siedzący tryb życia",
            "Lekka aktywność (1-3 dni/tydzień)",
            "Umiarkowana aktywność (3-5 dni/tydzień)",
            "Wysoka aktywność (6-7 dni/tydzień)",
            "Bardzo wysoka aktywność (2x dziennie, intensywne treningi)"
        )

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            activityLevels
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }

        spActivityLevel.adapter = adapter
        spActivityLevel.setSelection(2, false)
    }


    private fun getGoalsData(): GoalsData {
        val mainGoalIndex = sbMainGoal.progress.coerceIn(0, 2)
        val targetWeight = tvTargetWeight.text.toString().trim().toDoubleOrNull()
        val activityLevel = (spActivityLevel.selectedItemPosition + 1).coerceIn(1, 5)
        val freq = spTrainingFrequency.selectedItemPosition.coerceIn(0, 7)

        // Mapowanie z index na enum
        val mainGoal = when(mainGoalIndex) {
            0 -> MainGoal.LOSE
            1 -> MainGoal.MAINTAIN
            2 -> MainGoal.GAIN
            else -> MainGoal.MAINTAIN
        }

        return GoalsData(
            mainGoal = mainGoal,
            targetWeightKg = targetWeight,
            activityLevel = activityLevel,
            trainingFrequencyPerWeek = freq,
            notifyMeals = cbNotifyMeals.isChecked,
            notifyTraining = cbNotifyTraining.isChecked,
            notifyWeighIn = cbNotifyWeighIn.isChecked
        )
    }

    //zapisywanie do mongo
    @SuppressLint("DefaultLocale")
    private fun saveGoals(goals: GoalsData) {
        lifecycleScope.launch {
            try {
                userData?.let { user ->
                    val userCalculator = UserCalculator()
                    val age = user.getAge()
                    if (age == null) {
                        Toast.makeText(requireContext(), "Błąd przy obliczaniu wieku", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val bmi = userCalculator.calculateBMI(user.weight!!, user.height!!)
                    val roundedBMI = String.format("%.1f", bmi).toDouble()
                    val bmr = userCalculator.calculateBMR(
                        user.weight, user.height, age.toDouble(), user.sex
                    )
                    val roundedBMR = String.format("%.0f", bmr).toDouble()
                    val birthDateIso = user.getBirthDateAsIsoString() ?: ""

                    // Pobierz Firebase UID zalogowanego użytkownika
                    val firebaseUid = AuthManager.currentUserId
                    if (firebaseUid == null) {
                        Toast.makeText(requireContext(), "Błąd: Brak zalogowanego użytkownika", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // Przygotuj dane użytkownika
                    val createUserDto = CreateUserDto(
                        username = user.name,
                        auth = AuthData(
                            provider = "firebase",
                            firebaseUid = firebaseUid
                        ),
                        profile = ProfileData(
                            sex = user.sex,
                            birthDate = birthDateIso,
                            heightCm = user.height.toInt(),
                            weightKg = user.weight
                        ),
                        computed = ComputedData(
                            bmi = roundedBMI,
                            bmr = roundedBMR
                        ),
                        settings = SettingsData(
                            activityLevel = goals.activityLevel,
                            notifications = NotificationSettings(
                                enabled = goals.notifyMeals || goals.notifyTraining || goals.notifyWeighIn,
                                types = NotificationTypes(
                                    workoutReminders = goals.notifyTraining,
                                    mealReminders = goals.notifyMeals,
                                    measureReminders = goals.notifyWeighIn
                                )
                            ),
                            preferredTrainingFrequencyPerWeek = goals.trainingFrequencyPerWeek
                        )
                    )

                    val createdUser = NetworkModule.api.createUser(createUserDto)

                    val currentWeight = user.weight
                    val targetWeight = goals.targetWeight ?: currentWeight
                    val weightDifference = abs(currentWeight - targetWeight)
                    
                    val estimatedWeeks = if (weightDifference > 0) {
                        (weightDifference / 0.5).toInt().coerceIn(1, 52)
                    } else {
                        12
                    }

                    val activityMultiplier = when(goals.activityLevel) {
                        1 -> 1.2   // Brak aktywności/siedzący tryb życia
                        2 -> 1.375 // Lekka aktywność (1-3 dni/tydzień)
                        3 -> 1.55  // Umiarkowana aktywność (3-5 dni/tydzień)
                        4 -> 1.725 // Wysoka aktywność (6-7 dni/tydzień)
                        5 -> 1.9   // Bardzo wysoka aktywność (2x dziennie, intensywne treningi)
                        else -> 1.55
                    }
                    
                    val tdee = (roundedBMR * activityMultiplier).toInt()
                    val calorieTarget = when(goals.mainGoalKey) {
                        "lose_weight" -> tdee - 400  // deficyt 400 kcal
                        "gain_weight" -> tdee + 300  // nadwyżka 300 kcal
                        else -> tdee                 // utrzymanie
                    }

                    // Przygotuj dane celu
                    val createGoalDto = CreateUserGoalDto(
                        userId = createdUser.id,
                        type = when(goals.mainGoalKey) {
                            "lose_weight" -> "lose_weight"
                            "gain_weight" -> "gain_weight"
                            "maintain" -> "maintain"
                            else -> "maintain"
                        },
                        firstWeightKg = goals.firstWeight ?: 0.0,
                        targetWeightKg = goals.targetWeight ?: 0.0,
                        plan = GoalPlanData(
                            trainingFrequencyPerWeek = goals.trainingFrequencyPerWeek,
                            estimatedDurationWeeks = estimatedWeeks,
                            calorieTarget = calorieTarget
                        ),
                        startedAt = Instant.now().toString(),
                        notes = "Utworzono podczas onboardingu"
                    )

                    // Zapisz cel
                    NetworkModule.api.createUserGoal(createGoalDto)

                    Toast.makeText(requireContext(), "Dane i cele zapisane", Toast.LENGTH_SHORT).show()
                    
                    // Przekieruj do głównej aplikacji
                    val intent = Intent(requireActivity(), UserMainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    requireActivity().finish()
                } ?: run {
                    Toast.makeText(requireContext(), "Brak danych użytkownika", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTargetWeightPicker(weightGoal: String, currentWeight: Double?) {
        val currentWeightInt = currentWeight?.toInt() ?: 70

        if (weightGoal == "loose") {
            showNumberPicker(
                title = "Wybierz docelową wagę (kg)",
                currentValue = currentWeightInt,
                minValue = 30,
                maxValue = currentWeightInt
            ) { value ->
                tvTargetWeight.text = value.toString()
            }
        }
        else if (weightGoal == "gain") {
            showNumberPicker(
                title = "Wybierz docelową wagę (kg)",
                currentValue = tvTargetWeight.text.toString().toIntOrNull() ?: 70,
                minValue = currentWeightInt,
                maxValue = 200
            ) { value ->
                tvTargetWeight.text = value.toString()
            }
        }
    }

    private fun showNumberPicker(
        title: String,
        currentValue: Int,
        minValue: Int,
        maxValue: Int,
        onValueSelected: (Int) -> Unit
    ) {
        val numberPicker = NumberPicker(requireContext()).apply {
            this.minValue = minValue
            this.maxValue = maxValue
            value = currentValue.coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
        }

        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_NumberPicker)
            .setTitle(title)
            .setView(numberPicker)
            .setPositiveButton("OK") { _, _ ->
                onValueSelected(numberPicker.value)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
}

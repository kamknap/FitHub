package pl.fithubapp

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import pl.fithubapp.data.UpdateProfileData
import pl.fithubapp.data.UpdateUserDto
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import pl.fithubapp.data.ChallengeType
import pl.fithubapp.data.PointsManager
import pl.fithubapp.data.UserWeightHistoryDto
import pl.fithubapp.data.ChallengeManager
import pl.fithubapp.logic.UserCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class UserWeightFragment : Fragment(R.layout.fragment_user_weight) {

    private lateinit var userWeight: TextView
    private lateinit var userProgress: TextView
    private lateinit var btnAddWeight: ImageButton
    private lateinit var weightHistoryAdapter: WeightHistoryAdapter
    private lateinit var rvWeightHistory : RecyclerView
    private lateinit var weightChartView: WeightChartView
    private var currentUserWeight: Double? = null
    private var currentGoalType: String? = null
    private var referenceWeight: Double? = null
    private lateinit var btnRangeAll: Button
    private lateinit var btnRangeYear: Button
    private lateinit var btnRangeSixMonths: Button
    private lateinit var btnRangeThreeMonths: Button
    private lateinit var btnRangeMonth: Button
    private lateinit var btnRangeWeek: Button
    private var fullHistoryList: List<UserWeightHistoryDto> = emptyList()
    private var currentRange: DateRange = DateRange.THREE_MONTHS
    
    private val currentUserId: String
        get() = AuthManager.currentUserId ?: ""
    
    enum class DateRange {
        ALL, YEAR, SIX_MONTHS, THREE_MONTHS, MONTH, WEEK
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userWeight = view.findViewById(R.id.tvCurrentWeight)
        userProgress = view.findViewById(R.id.tvProgress)
        btnAddWeight = view.findViewById(R.id.btnAddWeight)
        rvWeightHistory = view.findViewById(R.id.rvWeightHistory)
        weightChartView = view.findViewById(R.id.weightChartView)
        btnRangeAll = view.findViewById(R.id.btnRangeAll)
        btnRangeYear = view.findViewById(R.id.btnRangeYear)
        btnRangeSixMonths = view.findViewById(R.id.btnRangeSixMonths)
        btnRangeThreeMonths = view.findViewById(R.id.btnRangeThreeMonths)
        btnRangeMonth = view.findViewById(R.id.btnRangeMonth)
        btnRangeWeek = view.findViewById(R.id.btnRangeWeek)


        getCurrentWeight()
        updateProgressStats()
        fillHistory()

        btnAddWeight.setOnClickListener {
            showAddWeightDialog()
        }

        btnRangeAll.setOnClickListener {
            currentRange = DateRange.ALL
            updateChartRange()
            highlightSelectedButton(btnRangeAll)
        }

        btnRangeYear.setOnClickListener {
            currentRange = DateRange.YEAR
            updateChartRange()
            highlightSelectedButton(btnRangeYear)
        }

        btnRangeSixMonths.setOnClickListener {
            currentRange = DateRange.SIX_MONTHS
            updateChartRange()
            highlightSelectedButton(btnRangeSixMonths)
        }

        btnRangeThreeMonths.setOnClickListener {
            currentRange = DateRange.THREE_MONTHS
            updateChartRange()
            highlightSelectedButton(btnRangeThreeMonths)
        }

        btnRangeMonth.setOnClickListener {
            currentRange = DateRange.MONTH
            updateChartRange()
            highlightSelectedButton(btnRangeMonth)
        }

        btnRangeWeek.setOnClickListener {
            currentRange = DateRange.WEEK
            updateChartRange()
            highlightSelectedButton(btnRangeWeek)
        }

        highlightSelectedButton(btnRangeThreeMonths)
    }

    private fun getCurrentWeight(){
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = NetworkModule.api.getCurrentUser()
                val userGoals = NetworkModule.api.getCurrentUserGoals()

                if (userGoals.isEmpty()) {
                    return@launch
                }

                currentUserWeight = user.profile.weightKg

                val activeGoal = userGoals.firstOrNull { it.status == "active" }
                    ?: userGoals.first()

                currentGoalType = activeGoal.type
                referenceWeight = activeGoal.firstWeightKg
                Log.d("UserWeightFragment", "firstMeasured: $referenceWeight")


                userWeight.text = "Obecna waga: ${user.profile.weightKg}kg, cel: ${activeGoal.targetWeightKg}kg"
                updateAdapter()
            }
            catch (e: Exception){
                Toast.makeText(
                    requireContext(),
                    "Błąd: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateProgressStats(){
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = NetworkModule.api.getCurrentUser()
                val userGoals = NetworkModule.api.getCurrentUserGoals()

                if (userGoals.isEmpty()) {
                    userProgress.text = "Brak aktywnego celu"
                    return@launch
                }

                val currentWeight = user.profile.weightKg

                val activeGoal = userGoals.firstOrNull { it.status == "active" }
                    ?: userGoals.first()

                val goalType = activeGoal.type

                currentGoalType = goalType

                val calculator = UserCalculator()
                val result = calculator.calculateGoalProgress(currentWeight, activeGoal)

                userProgress.text = result.fullDesc
            }
            catch (e: Exception){
                Log.e("UserWeightFragment", "Błąd: ${e.message}", e)
                Toast.makeText(
                    requireContext(),
                    "Błąd: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showAddWeightDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_weight, null)
        
        val etWeight = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWeight)
        val tvDate = view.findViewById<TextView>(R.id.tvDate)

        val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        tvDate.text = currentDate

        etWeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()

                if (text.contains(".")) {
                    val parts = text.split(".")

                    if (parts.size > 2) {
                        s?.delete(text.length - 1, text.length)
                        return
                    }

                    if (parts.size == 2 && parts[1].length > 1) {
                        s?.delete(text.length - 1, text.length)
                        return
                    }
                }
            }
        })

        val dialog = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setView(view)
            .setPositiveButton("Dodaj") { _, _ ->
                val weight = etWeight.text.toString().toDoubleOrNull()

                if (weight == null || weight <= 0) {
                    Toast.makeText(requireContext(), "Podaj prawidłową wagę", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                addWeight(weight)
            }
            .setNegativeButton("Anuluj", null)
            .create()

        dialog.setOnShowListener {
            etWeight.requestFocus()
            etWeight.postDelayed({
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                        as InputMethodManager
                imm.showSoftInput(etWeight, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        dialog.show()
    }

    private fun addWeight(weight: Double){
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentUser = NetworkModule.api.getCurrentUser()

                val userProgress = NetworkModule.api.getUserProgress()
                val activeChallenge = userProgress.activeChallenges
                
                if (activeChallenge != null) {
                    val weightHistory = NetworkModule.api.getUserWeightHistory()
                    val challengeStartDate = java.time.ZonedDateTime.parse(activeChallenge.startedDate)
                    
                    val startWeight = weightHistory
                        .sortedBy { it.measuredAt }
                        .lastOrNull { 
                            val measuredDate = java.time.ZonedDateTime.parse(it.measuredAt)
                            !measuredDate.isAfter(challengeStartDate)
                        }?.weightKg ?: currentUser.profile.weightKg
                    
                    val totalWeightLoss = startWeight - weight
                    ChallengeManager.checkChallengeProgress(ChallengeType.WEIGHT_LOSS, totalWeightLoss)
                }

                val updateUserDto = UpdateUserDto(
                    profile = UpdateProfileData(
                        sex = currentUser.profile.sex,
                        birthDate = currentUser.profile.birthDate,
                        heightCm = currentUser.profile.heightCm,
                        weightKg = weight
                    )
                )
                NetworkModule.api.updateUser(updateUserDto)

                try {
                    Log.d("AddWeight", "Waga dodana, przyznaję punkty...")
                    val leveledUp = PointsManager.addPoints(PointsManager.ActionType.WEIGHT)

                    if (leveledUp) {
                        (activity as? UserMainActivity)?.showLevelUpDialog()
                    }
                } catch (e: Exception) {
                    Log.e("AddWeight", "Nie udało się dodać punktów: ${e.message}")
                }

                try {
                    val userGoals = NetworkModule.api.getCurrentUserGoals()
                    val activeGoal = userGoals.firstOrNull { it.status == "active" }

                    if (activeGoal != null) {
                        val targetReached = when (activeGoal.type) {
                            "lose_weight" -> weight <= activeGoal.targetWeightKg
                            "gain_weight" -> weight >= activeGoal.targetWeightKg
                            "maintain" -> false
                            else -> false
                        }

                        if (targetReached) {
                            PointsManager.addPoints(PointsManager.ActionType.GOAL_REACHED)

                            val message = when (activeGoal.type) {
                                "lose_weight" -> "Gratulacje! Osiągnąłeś wagę docelową ${activeGoal.targetWeightKg} kg! 🎉"
                                "gain_weight" -> "Gratulacje! Osiągnąłeś wagę docelową ${activeGoal.targetWeightKg} kg! 💪"
                                else -> "Gratulacje! Cel osiągnięty!"
                            }

                            PointsManager.onGoalAchieved?.invoke(message, 100)

                            val user = NetworkModule.api.getCurrentUser()
                            val bmr = user.computed.bmr.toInt()
                            val activityLevel = user.settings?.activityLevel ?: 3
                            val activityMultiplier = when(activityLevel) {
                                1 -> 1.2
                                2 -> 1.375
                                3 -> 1.55
                                4 -> 1.725
                                5 -> 1.9
                                else -> 1.55
                            }
                            val tdee = (bmr * activityMultiplier).toInt()

                            val updateDto = pl.fithubapp.data.UpdateUserGoalDto(
                                type = "maintain",
                                firstWeightKg = weight,
                                targetWeightKg = weight,
                                plan = pl.fithubapp.data.GoalPlanData(
                                    trainingFrequencyPerWeek = activeGoal.plan.trainingFrequencyPerWeek,
                                    estimatedDurationWeeks = null,
                                    calorieTarget = tdee
                                )
                            )

                            NetworkModule.api.updateUserGoal(activeGoal.id, updateDto)
                            Log.d("AddWeight", "Cel automatycznie zmieniony na 'utrzymać' z wagą ${weight} kg")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AddWeight", "Błąd sprawdzania celu: ${e.message}")
                }

                Toast.makeText(
                    requireContext(),
                    "Waga zaktualizowana: ${String.format("%.1f", weight)}kg",
                    Toast.LENGTH_SHORT
                ).show()

                getCurrentWeight()
                updateProgressStats()
                fillHistory()
            }
            catch (e: Exception){
                Log.e("UserWeightFragment", "Błąd dodawania pomiaru", e)
                Toast.makeText(
                    requireContext(),
                    "Błąd: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fillHistory(){
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userHistoryList = NetworkModule.api.getUserWeightHistory()

                fullHistoryList = userHistoryList

                val sortedList = userHistoryList.sortedByDescending { it.measuredAt }

                weightHistoryAdapter = WeightHistoryAdapter(referenceWeight, currentGoalType)
                rvWeightHistory.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = weightHistoryAdapter
                }
                weightHistoryAdapter.submitList(sortedList)

                updateChartRange()
                Log.d("WeightHistory", "Załadowano ${sortedList.size} pomiarów")
            }
            catch(e: Exception){
                Log.e("UserWeightFragment", "Błąd", e)
            }
        }
    }

    private fun updateAdapter() {
        if (::weightHistoryAdapter.isInitialized) {
            weightHistoryAdapter = WeightHistoryAdapter(referenceWeight, currentGoalType)
            rvWeightHistory.adapter = weightHistoryAdapter
            fillHistory()
        }
    }

    private fun highlightSelectedButton(selectedButton: Button) {
        listOf(
            btnRangeAll,
            btnRangeYear,
            btnRangeSixMonths,
            btnRangeThreeMonths,
            btnRangeMonth,
            btnRangeWeek
        ).forEach { button ->
            button.setBackgroundColor(resources.getColor(android.R.color.transparent, null))
            button.setTextColor(resources.getColor(R.color.purple_primary, null))
        }

        selectedButton.setBackgroundColor(resources.getColor(R.color.purple_primary, null))
        selectedButton.setTextColor(resources.getColor(R.color.white, null))
    }
    private fun updateChartRange() {
        val filteredData = filterDataByRange(fullHistoryList, currentRange)

        if (filteredData.isEmpty()) {
            Log.d("WeightChart", "Brak danych dla zakresu $currentRange")
            weightChartView.setData(emptyList())
        } else {
            weightChartView.setData(filteredData)
            Log.d("WeightChart", "Pokazuję ${filteredData.size} pomiarów dla zakresu $currentRange")
        }
    }

    private fun filterDataByRange(data: List<UserWeightHistoryDto>, range: DateRange): List<UserWeightHistoryDto> {
        if (range == DateRange.ALL) {
            return data
        }

        val now = ZonedDateTime.now()
        val cutoffDate = when (range) {
            DateRange.YEAR -> now.minusYears(1)
            DateRange.SIX_MONTHS -> now.minusMonths(6)
            DateRange.THREE_MONTHS -> now.minusMonths(3)
            DateRange.MONTH -> now.minusMonths(1)
            DateRange.WEEK -> now.minusDays(7)
            else -> return data
        }

        return data.filter { history ->
            try {
                val measureDate = Instant.parse(history.measuredAt).atZone(ZoneId.systemDefault())
                measureDate.isAfter(cutoffDate)
            } catch (e: Exception) {
                false
            }
        }
    }
}
package pl.fithubapp

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import pl.fithubapp.data.OnboardingUserData
import pl.fithubapp.logic.UserCalculator
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class UserDataFragment : Fragment(R.layout.fragment_user_data) {
    private lateinit var etWeight: EditText
    private lateinit var etHeight: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var etSex: EditText
    private lateinit var tvBMIResult: TextView
    private lateinit var tvBMRResult: TextView
    private lateinit var etName: EditText
    private lateinit var btnSave: Button
    private val userCalculator = UserCalculator()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupClickListeners()
        setupTextWatchers()
    }

    private fun initViews(view: View) {
        etWeight = view.findViewById(R.id.etWeight)
        etHeight = view.findViewById(R.id.etHeight)
        etBirthDate = view.findViewById(R.id.etAge)
        etSex = view.findViewById(R.id.etSex)
        tvBMIResult = view.findViewById(R.id.tvBMIResult)
        tvBMRResult = view.findViewById(R.id.tvBMRResult)
        etName = view.findViewById(R.id.etName)
        btnSave = view.findViewById(R.id.btnSave)
    }

    private fun setupClickListeners() {
        etWeight.setOnClickListener { showWeightPicker() }
        etHeight.setOnClickListener { showHeightPicker() }
        etBirthDate.setOnClickListener { showAgePicker() }
        etSex.setOnClickListener { showSexPicker() }

        btnSave.setOnClickListener {
            val userData = getUserData()
            if (userData.isComplete()) {
                (requireActivity() as? OnboardingActivity)?.showGoalsFragment(userData)
            } else {
                Toast.makeText(requireContext(), "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateCalculations()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        listOf(etWeight, etHeight, etBirthDate, etSex).forEach {
            it.addTextChangedListener(watcher)
        }
    }

    private fun getUserData(): OnboardingUserData {
        val sexDisplay = etSex.text.toString()
        // Mapowanie z polskiego (z UI) na angielski (do modelu danych)
        val sex = when(sexDisplay) {
            "Mężczyzna" -> "Male"
            "Kobieta" -> "Female"
            else -> "Male"
        }
        
        return OnboardingUserData(
            name = etName.text.toString().trim(),
            weight = etWeight.text.toString().toDoubleOrNull(),
            height = etHeight.text.toString().toDoubleOrNull(),
            birthDate = etBirthDate.text.toString(),
            sex = sex
        )
    }

    private fun updateCalculations() {
        val userData = getUserData()
        val age = userData.getAge()

        tvBMIResult.visibility = View.INVISIBLE
        tvBMRResult.visibility = View.INVISIBLE

        if (userData.isValidForBMI()) {
            calculateAndShowBMI(userData.weight!!, userData.height!!)
        }

        if (userData.isValidForBMR()) {
            calculateAndShowBMR(userData.weight!!, userData.height!!, age!!, userData.sex)
        }
    }

    private fun calculateAndShowBMI(weight: Double, height: Double) {
        val bmi = userCalculator.calculateBMI(weight, height)
        if (bmi != null) {
            val roundedBMI = String.format("%.1f", bmi)
            val bmiCategory = userCalculator.getBMICategory(bmi)
            tvBMIResult.apply {
                visibility = View.VISIBLE
                text = "Twoje BMI wynosi: $roundedBMI ($bmiCategory)"
            }
        }
    }

    private fun calculateAndShowBMR(weight: Double, height: Double, age: Int, sex: String) {
        val bmr = userCalculator.calculateBMR(weight, height, age.toDouble(), sex)
        if (bmr != null) {
            val roundedBMR = String.format("%.0f", bmr)
            tvBMRResult.apply {
                visibility = View.VISIBLE
                text = "Twoja podstawowa przemiana materii (BMR) wynosi około: $roundedBMR kcal"
            }
        }
    }

    private fun showWeightPicker() {
        showNumberPicker(
            title = "Wybierz wagę (kg)",
            currentValue = etWeight.text.toString().toIntOrNull() ?: 70,
            minValue = 30,
            maxValue = 200
        ) { value ->
            etWeight.setText(value.toString())
        }
    }

    private fun showHeightPicker() {
        showNumberPicker(
            title = "Wybierz wzrost (cm)",
            currentValue = etHeight.text.toString().toIntOrNull() ?: 180,
            minValue = 80,
            maxValue = 250
        ) { value ->
            etHeight.setText(value.toString())
        }
    }

    private fun showAgePicker() {
        var selectedDate = LocalDate.now().minusYears(25)

        val currentDateText = etBirthDate.text.toString()
        if (currentDateText.isNotEmpty()) {
            try {
                selectedDate = LocalDate.parse(currentDateText, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            } catch (e: Exception) {
            }
        }

        DatePickerDialog(
            requireContext(),
            R.style.ThemeOverlay_Fithub_Dialog,
            { _, year, month, dayOfMonth ->
                val date = LocalDate.of(year, month + 1, dayOfMonth)
                val formattedDate = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                etBirthDate.setText(formattedDate)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
            datePicker.minDate = LocalDate.now().minusYears(120)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            setTitle("Wybierz datę urodzenia")
            show()
        }
    }

    private fun showSexPicker() {
        val values = arrayOf("Mężczyzna", "Kobieta")
        val currentValue = etSex.text.toString()
        val currentIndex = values.indexOf(currentValue).takeIf { it >= 0 } ?: 0

        val numberPicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = values.size - 1
            displayedValues = values
            value = currentIndex
            wrapSelectorWheel = false
            
        }

        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_NumberPicker)
            .setTitle("Wybierz płeć")
            .setView(numberPicker)
            .setPositiveButton("OK") { _, _ ->
                etSex.setText(values[numberPicker.value])
            }
            .setNegativeButton("Anuluj", null)
            .show()
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
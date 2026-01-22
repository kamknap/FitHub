package pl.fithubapp

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import pl.fithubapp.data.ComputedData
import pl.fithubapp.data.UpdateProfileData
import pl.fithubapp.data.UpdateUserDto
import pl.fithubapp.logic.UserCalculator
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class EditProfileDialogFragment : DialogFragment() {

    private lateinit var etName: com.google.android.material.textfield.TextInputEditText
    private lateinit var etWeight: com.google.android.material.textfield.TextInputEditText
    private lateinit var etHeight: com.google.android.material.textfield.TextInputEditText
    private lateinit var etBirthDate: com.google.android.material.textfield.TextInputEditText
    private lateinit var etSex: com.google.android.material.textfield.TextInputEditText
    private lateinit var alertDialog: AlertDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.fragment_edit_profile_dialog, null)

        etName = view.findViewById(R.id.etName)
        etWeight = view.findViewById(R.id.etWeight)
        etHeight = view.findViewById(R.id.etHeight)
        etBirthDate = view.findViewById(R.id.etBirthDate)
        etSex = view.findViewById(R.id.etSex)

        arguments?.let {
            etName.setText(it.getString("userName"))
            etWeight.setText(it.getDouble("userWeight").toInt().toString())
            etHeight.setText(it.getInt("userHeight").toString())

            val birthDateIso = it.getString("userBirthDate")
            if (birthDateIso != null) {
                try {
                    val displayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    val date = if (birthDateIso.contains("T")) {
                        LocalDate.parse(birthDateIso.substring(0, 10))
                    } else {
                        LocalDate.parse(birthDateIso)
                    }
                    etBirthDate.setText(date.format(displayFormat))
                } catch (e: Exception) {
                    etBirthDate.setText(birthDateIso)
                }
            }

            val sex = it.getString("userSex")
            // Mapowanie z angielskiego (z API) na polski (do wyświetlenia)
            etSex.setText(when(sex) {
                "Male" -> "Mężczyzna"
                "Female" -> "Kobieta"
                else -> "Mężczyzna"
            })
        }

        setupClickListeners()

        alertDialog = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setTitle("Edytuj profil")
            .setView(view)
            .setPositiveButton("Zapisz", null) // Ustawione na null - obsługa niżej
            .setNegativeButton("Anuluj", null)
            .create()

        alertDialog.setOnShowListener {
            val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                saveProfile()
            }
        }

        return alertDialog
    }

    private fun setupClickListeners() {
        etWeight.setOnClickListener { showWeightPicker() }
        etHeight.setOnClickListener { showHeightPicker() }
        etBirthDate.setOnClickListener { showDatePicker() }
        etSex.setOnClickListener { showSexPicker() }
    }

    private fun saveProfile() {
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false

        lifecycleScope.launch {
            try {
                val calculator = UserCalculator()
                val weight = etWeight.text.toString().replace(",", ".").toDoubleOrNull()
                val height = etHeight.text.toString().toIntOrNull()
                val birthDateDisplay = etBirthDate.text.toString()
                val sexDisplay = etSex.text.toString()
                
                val sex = when(sexDisplay) {
                    "Mężczyzna" -> "Male"
                    "Kobieta" -> "Female"
                    else -> "Male"
                }

                if (weight == null || height == null) {
                    Toast.makeText(context, "Nieprawidłowa waga lub wzrost", Toast.LENGTH_SHORT).show()
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                    return@launch
                }

                val displayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val birthDate = try {
                    val date = LocalDate.parse(birthDateDisplay, displayFormat)
                    date.format(isoFormat)
                } catch (e: Exception) {
                    Log.e("EditProfile", "Błąd konwersji daty: ${e.message}")
                    birthDateDisplay
                }

                val age = calculator.calculateAge(birthDate)

                Log.d("EditProfile", "Waga: $weight, Wzrost: $height, Wiek: $age, Płeć: $sex")

                val bmi = calculator.calculateBMI(weight, height.toDouble())
                val bmr = calculator.calculateBMR(weight, height.toDouble(), age.toDouble(), sex)

                Log.d("EditProfile", "BMI: $bmi, BMR: $bmr")

                val updateDto = UpdateUserDto(
                    username = etName.text.toString(),
                    profile = UpdateProfileData(
                        weightKg = weight,
                        heightCm = height,
                        sex = sex,
                        birthDate = birthDate
                    ),
                    computed = ComputedData(
                        bmi = bmi ?: 0.0,
                        bmr = bmr ?: 0.0
                    )
                )

                Log.d("EditProfile", "Wysyłam dane: $updateDto")

                val response = NetworkModule.api.updateUser(updateDto)

                Log.d("EditProfile", "Odpowiedź: $response")

                Toast.makeText(context, "Profil zaktualizowany", Toast.LENGTH_SHORT).show()

                (parentFragment as? UserProfileFragment)?.loadDataForUser()

                dismiss()

            } catch (e: Exception) {
                Log.e("EditProfile", "Błąd aktualizacji: ${e.message}", e)
                Toast.makeText(context, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()

                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
            }
        }
    }


    private fun showWeightPicker() {
        showNumberPicker("Waga (kg)", etWeight.text.toString().toIntOrNull() ?: 70, 30, 200) {
            etWeight.setText(it.toString())
        }
    }

    private fun showHeightPicker() {
        showNumberPicker("Wzrost (cm)", etHeight.text.toString().toIntOrNull() ?: 170, 80, 250) {
            etHeight.setText(it.toString())
        }
    }

    private fun showDatePicker() {
        var selectedDate = LocalDate.now().minusYears(25)

        try {
            val displayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val parsed = LocalDate.parse(etBirthDate.text.toString(), displayFormat)
            selectedDate = parsed
        } catch (e: Exception) {

        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = String.format("%02d/%02d/%04d", day, month + 1, year)
                etBirthDate.setText(date)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
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

    private fun showNumberPicker(title: String, current: Int, min: Int, max: Int, onSelected: (Int) -> Unit) {
        val picker = NumberPicker(requireContext()).apply {
            minValue = min
            maxValue = max
            value = current
            wrapSelectorWheel = false
        }
        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_NumberPicker)
            .setTitle(title)
            .setView(picker)
            .setPositiveButton("OK") { _, _ -> onSelected(picker.value) }
            .setNegativeButton("Anuluj", null)
            .show()
    }
}
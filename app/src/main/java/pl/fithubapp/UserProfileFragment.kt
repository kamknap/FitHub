package pl.fithubapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import pl.fithubapp.logic.UserCalculator
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord


class UserProfileFragment : Fragment(R.layout.fragment_user_profile) {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserAge: TextView
    private lateinit var tvUserWeight: TextView
    private lateinit var tvUserSex: TextView
    private lateinit var tvUserBMI: TextView
    private lateinit var tvUserBMR: TextView
    private lateinit var tvUserGoal: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var btnEditGoals: Button
    private lateinit var btnConnectSmartwatch: Button
    private lateinit var btnContactAuthor: Button
    private lateinit var btnLogout: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserAge = view.findViewById(R.id.tvUserAge)
        tvUserWeight = view.findViewById(R.id.tvUserWeight)
        tvUserSex = view.findViewById(R.id.tvUserSex)
        tvUserBMI = view.findViewById(R.id.tvUserBMI)
        tvUserBMR = view.findViewById(R.id.tvUserBMR)
        tvUserGoal = view.findViewById(R.id.tvUserGoal)
        btnEditProfile = view.findViewById(R.id.btnEditProfile)
        btnEditGoals = view.findViewById(R.id.btnEditGoals)
        btnConnectSmartwatch = view.findViewById(R.id.btnConnectSmartwatch)
        btnContactAuthor= view.findViewById(R.id.btnContactAuthor)
        btnLogout = view.findViewById(R.id.btnLogout)

        setupButtonListeners(btnEditProfile, btnEditGoals, btnConnectSmartwatch,btnContactAuthor, btnLogout)
        
        loadDataForUser()

    }

    private fun setupButtonListeners(
        btnEditProfile: Button,
        btnEditGoals: Button,
        btnConnectSmartwatch: Button,
        btnContactAuthor: Button,
        btnLogout: Button
    ) {
        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        btnEditGoals.setOnClickListener {
            showEditGoalsDialog()
        }

        val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))
        val requestPermissionContract = PermissionController.createRequestPermissionResultContract()
        val requestPermissions = registerForActivityResult(requestPermissionContract) { granted ->
            if (granted.containsAll(permissions)) {
                Toast.makeText(requireContext(), "Połączono! Dane o krokach i wadze będą synchronizowane automatycznie.", Toast.LENGTH_LONG).show()
                updateButtonConnectedState(true)
            } else {
                Toast.makeText(requireContext(), "Nie przyznano wszystkich uprawnień do synchronizacji danych.", Toast.LENGTH_LONG).show()
                updateButtonConnectedState(false)
            }
        }
        
        btnConnectSmartwatch.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val availabilityStatus = HealthConnectClient.getSdkStatus(requireContext())
                    
                    when (availabilityStatus) {
                        HealthConnectClient.SDK_AVAILABLE -> {
                            val healthConnectClient = HealthConnectClient.getOrCreate(requireContext())
                            val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
                            
                            if (grantedPermissions.containsAll(permissions)) {
                                Toast.makeText(
                                    requireContext(),
                                    "Dane o krokach i wadze synchronizowane są automatycznie.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                // Nie połączony
                                requestPermissions.launch(permissions)
                            }
                        }
                        HealthConnectClient.SDK_UNAVAILABLE -> {
                            Toast.makeText(
                                requireContext(),
                                "Health Connect nie jest dostępny na tym urządzeniu (wymaga Android 14+)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                            Toast.makeText(
                                requireContext(),
                                "Zaktualizuj Health Connect w Google Play Store",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            Toast.makeText(
                                requireContext(),
                                "Nieznany status Health Connect",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UserProfile", "Błąd sprawdzania Health Connect", e)
                    Toast.makeText(
                        requireContext(),
                        "Błąd: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnContactAuthor.setOnClickListener {
            val recipient = "knapikkamil2002@gmail.com"
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            }
            try {
                startActivity(intent)
            }
            catch (e: ActivityNotFoundException){
                Toast.makeText(context, "Nie znaleziono aplikacji pocztowej", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setTitle("Wylogowanie")
            .setMessage("Czy na pewno chcesz się wylogować?")
            .setPositiveButton("Tak") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Nie", null)
            .show()
    }

    private fun performLogout() {
        AuthManager.logout()
        Toast.makeText(context, "Zostałeś wylogowany", Toast.LENGTH_SHORT).show()
        
        // do SplashActivity, które przekieruje do LoginActivity
        val intent = Intent(requireContext(), SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun showEditProfileDialog() {
        lifecycleScope.launch {
            try {
                val user = NetworkModule.api.getCurrentUser()

                val dialog = EditProfileDialogFragment().apply {
                    arguments = Bundle().apply {
                        putString("userId", user.id)
                        putString("userName", user.username)
                        putString("userSex", user.profile.sex)
                        putString("userBirthDate", user.profile.birthDate)
                        putDouble("userWeight", user.profile.weightKg)
                        putInt("userHeight", user.profile.heightCm)
                    }
                }

                dialog.show(childFragmentManager, "EditProfileDialog")

            } catch (e: Exception) {
                Toast.makeText(context, "Błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditGoalsDialog() {
        lifecycleScope.launch {
            try {
                val user = NetworkModule.api.getCurrentUser()
                val userGoals = NetworkModule.api.getCurrentUserGoals()
                val activeGoal = userGoals.find { it.status == "active" }

                val currentActivityLevel = user.settings?.activityLevel ?: 1
                val currentTrainingFreq = user.settings?.preferredTrainingFrequencyPerWeek ?: 3

                val dialog = EditGoalsDialogFragment().apply {
                    arguments = Bundle().apply {
                        putString("userId", user.id)
                        putString("goalId", activeGoal?.id)
                        putString("goalType", activeGoal?.type)
                        putDouble("firstWeight", activeGoal?.firstWeightKg ?: user.profile.weightKg)
                        putDouble("targetWeight", activeGoal?.targetWeightKg ?: user.profile.weightKg)
                        putInt("activityLevel", currentActivityLevel)
                        putInt("trainingFrequency", currentTrainingFreq)
                    }
                }

                dialog.show(childFragmentManager, "EditGoalsDialog")

            } catch (e: Exception) {
                Log.e("UserProfile", "Błąd otwierania celów", e)
                Toast.makeText(context, "Błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadDataForUser()
        checkSmartwatchConnectionStatus()
    }

    private fun checkSmartwatchConnectionStatus() {
        lifecycleScope.launch {
            try {
                val availabilityStatus = HealthConnectClient.getSdkStatus(requireContext())
                
                if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
                    val healthConnectClient = HealthConnectClient.getOrCreate(requireContext())
                    val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(
                        WeightRecord::class))
                    val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
                    
                    if (grantedPermissions.containsAll(permissions)) {
                        updateButtonConnectedState(true)
                    } else {
                        updateButtonConnectedState(false)
                    }
                } else {
                    updateButtonConnectedState(false)
                }
            } catch (e: Exception) {
                Log.e("UserProfile", "Błąd sprawdzania stanu połączenia", e)
                updateButtonConnectedState(false)
            }
        }
    }

    private fun updateButtonConnectedState(isConnected: Boolean) {
        if (isConnected) {
            btnConnectSmartwatch.text = "✓ Health Connect połączony"
            btnConnectSmartwatch.backgroundTintList = resources.getColorStateList(R.color.green_success, null)
            btnConnectSmartwatch.setTextColor(resources.getColor(R.color.white, null))
            btnConnectSmartwatch.alpha = 1.0f
        } else {
            btnConnectSmartwatch.text = "Podłącz Health Connect"
            btnConnectSmartwatch.backgroundTintList = resources.getColorStateList(R.color.blue_info, null)
            btnConnectSmartwatch.setTextColor(resources.getColor(R.color.white, null))
            btnConnectSmartwatch.alpha = 1.0f
        }
    }

    fun loadDataForUser(){
        lifecycleScope.launch {
            try {
                val user = NetworkModule.api.getCurrentUser()
                val userGoals = NetworkModule.api.getCurrentUserGoals()
                val calculator = UserCalculator()

                tvUserName.text = user.username
                val age = calculator.calculateAge(user.profile.birthDate)
                tvUserAge.text = "$age lat"
                tvUserWeight.text = "${user.profile.weightKg} kg"
                tvUserSex.text = when(user.profile.sex) {
                    "Male" -> "Mężczyzna"
                    "Female" -> "Kobieta"
                    else -> "Mężczyzna"
                }
                tvUserBMI.text = String.format("%.1f", user.computed.bmi)
                tvUserBMR.text = "${user.computed.bmr.toInt()} kcal"

                val activeGoal = userGoals.find { it.status == "active" }
                if (activeGoal != null) {
                    tvUserGoal.text = "${activeGoal.firstWeightKg} → ${activeGoal.targetWeightKg} kg"
                } else {
                    tvUserGoal.text = "Brak aktywnego celu"
                }

            }catch (e: Exception){
                Log.d("Blad", e.toString())
                Toast.makeText(context, "Błąd ładowania danych: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
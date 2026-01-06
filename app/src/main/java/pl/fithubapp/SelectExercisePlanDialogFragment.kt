package pl.fithubapp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import pl.fithubapp.data.CreateUserExercisePlanDto
import kotlinx.coroutines.launch

class SelectExercisePlanDialogFragment : DialogFragment() {

    interface OnPlanSelectedListener {
        fun onPlanSelected(planId: String, planName: String)
    }

    var onPlanSelectedListener: OnPlanSelectedListener? = null
    private var plansContainer: LinearLayout? = null
    private var userId: String? = null
    private var currentPlanId: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        userId = arguments?.getString("userId") ?: return super.onCreateDialog(savedInstanceState)
        currentPlanId = arguments?.getString("currentPlanId")

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_select_exercise_plan, null)

        plansContainer = view.findViewById(R.id.llPlansContainer)
        val btnNewPlan = view.findViewById<MaterialButton>(R.id.btnNewPlan)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)

        btnNewPlan.setOnClickListener {
            showCreatePlanDialog(userId!!)
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        loadPlans()

        return AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setView(view)
            .create()
    }

    private fun loadPlans() {
        lifecycleScope.launch {
            try {
                val plans = NetworkModule.api.getUserExercisePlans()

                plansContainer?.removeAllViews()

                plans.forEach { plan ->
                    val planItemView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_exercise_plan, plansContainer, false)

                    val tvPlanName = planItemView.findViewById<TextView>(R.id.tvPlanName)
                    val tvPlanInfo = planItemView.findViewById<TextView>(R.id.tvPlanInfo)
                    val btnDeletePlan = planItemView.findViewById<ImageButton>(R.id.btnDeletePlan)
                    val llPlanClickArea = planItemView.findViewById<LinearLayout>(R.id.llPlanClickArea)

                    tvPlanName.text = plan.planName
                    
                    val exerciseCount = plan.planExercises?.size ?: 0
                    tvPlanInfo.text = when (exerciseCount) {
                        0 -> "Brak ćwiczeń"
                        1 -> "1 ćwiczenie"
                        in 2..4 -> "$exerciseCount ćwiczenia"
                        else -> "$exerciseCount ćwiczeń"
                    }

                    if (plan.id == currentPlanId) {
                        (planItemView as? androidx.cardview.widget.CardView)?.apply {
                            setCardBackgroundColor(resources.getColor(R.color.blue_info_very_light, null))
                        }
                    }

                    llPlanClickArea.setOnClickListener {
                        onPlanSelectedListener?.onPlanSelected(plan.id, plan.planName)
                        dismiss()
                    }

                    btnDeletePlan.setOnClickListener {
                        showDeleteConfirmationDialog(plan.id, plan.planName)
                    }

                    plansContainer?.addView(planItemView)
                }

                if (plans.isEmpty()) {
                    val emptyView = LayoutInflater.from(requireContext())
                        .inflate(android.R.layout.simple_list_item_1, plansContainer, false)
                    emptyView.findViewById<TextView>(android.R.id.text1).apply {
                        text = "Brak planów treningowych"
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setPadding(24, 24, 24, 24)
                    }
                    plansContainer?.addView(emptyView)
                }

            } catch (e: Exception) {
                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), "Błąd: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(planId: String, planName: String) {
        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setTitle("Usuwanie planu")
            .setMessage("Czy na pewno chcesz usunąć plan \"$planName\"?")
            .setPositiveButton("Usuń") { _, _ ->
                deletePlan(planId, planName)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun deletePlan(planId: String, planName: String) {
        lifecycleScope.launch {
            try {
                NetworkModule.api.deleteUserExercisePlan(planId)

                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), "Usunięto plan: $planName", Toast.LENGTH_SHORT).show()

                    if (planId == currentPlanId) {
                        val remainingPlans = NetworkModule.api.getUserExercisePlans()
                        if (remainingPlans.isNotEmpty()) {
                            onPlanSelectedListener?.onPlanSelected(remainingPlans[0].id, remainingPlans[0].planName)
                        } else {
                            // Brak planów - utworzy domyślny
                            onPlanSelectedListener?.onPlanSelected("", "")
                        }
                    }

                    loadPlans() // Odśwież listę planów
                }
            } catch (e: Exception) {
                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), "Błąd usuwania: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showCreatePlanDialog(userId: String) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_create_exercise_plan, null)

        val etPlanName = view.findViewById<TextInputEditText>(R.id.etPlanName)

        val createDialog = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setView(view)
            .setPositiveButton("Utwórz") { _, _ ->
                val planName = etPlanName.text.toString().trim()
                if (planName.isNotEmpty()) {
                    createPlan(userId, planName)
                } else {
                    Toast.makeText(requireContext(), "Podaj nazwę planu", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj", null)
            .create()

        createDialog.show()
    }

    private fun createPlan(userId: String, planName: String) {
        lifecycleScope.launch {
            try {
                val newPlan = NetworkModule.api.createUserExercisePlan(
                    CreateUserExercisePlanDto(
                        planName = planName
                    )
                )

                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), "Utworzono: ${newPlan.planName}", Toast.LENGTH_SHORT).show()
                    onPlanSelectedListener?.onPlanSelected(newPlan.id, newPlan.planName)
                    dismiss()
                }
            } catch (e: Exception) {
                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), "Błąd: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        fun newInstance(userId: String, currentPlanId: String? = null): SelectExercisePlanDialogFragment {
            return SelectExercisePlanDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("userId", userId)
                    putString("currentPlanId", currentPlanId)
                }
            }
        }
    }
}
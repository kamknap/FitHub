package pl.fithubapp

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import pl.fithubapp.logic.UserCalculator

class UserCalculatorTest {
    private lateinit var calculator: UserCalculator

    @Before
    fun setUp(){
        calculator = UserCalculator()
    }

    @Test
    fun calculateBMICorrectValues(){
        val weight = 80.0
        val height = 180.0

        val result = calculator.calculateBMI(weight, height)

        Assert.assertNotNull(result)
        Assert.assertTrue("BMI wyniosło: $result",result!! in 24.5..25.0)
    }

    @Test
    fun calculateBMRCorrectValues(){
        val weight = 80.0
        val height = 180.0
        val sex = "Male"
        val age = 26.0

        val result = calculator.calculateBMR(weight, height, age, sex)
        Assert.assertNotNull(result)
        Assert.assertTrue("BMR powinno wynieść około 1800kcal, a wyniosło: $result", result!! in 1850.0..1900.0)
    }

    @Test
    fun calculateEnergyExpenditureCorrectValues(){
        val weight = 80.0
        val mets = 8.0
        val minutes = 30.0

        val result = calculator.calculateEnergyExpenditure(weight, mets, minutes)
        Assert.assertNotNull(result)
        Assert.assertTrue("Spalone kalorie powinny wynieść około 320kcal, a wyniosło: $result", result!! in 300.0..340.0)
    }

    @Test
    fun calculateMacroTargetsCorrectValues(){
        val calorieGoal = 1800.0

        val result = calculator.calculateMacroTargets(calorieGoal)
        Assert.assertNotNull(result)
        Assert.assertTrue("Tłuszcze powinny wynieść około 60 gramów, a wyniosły: ${result.fat}", result.fat in 50..70)
        Assert.assertTrue("Węglowodany powinny wynieść około 225 gramów, a wyniosły: ${result.carbs}", result.carbs in 215..235)
        Assert.assertTrue("Białko powinno wynieść około 90 gramów, a wyniosło: ${result.protein}", result.protein in 80..100)
    }
}
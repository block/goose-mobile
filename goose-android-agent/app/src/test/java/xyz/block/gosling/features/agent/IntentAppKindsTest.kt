package xyz.block.gosling.features.agent

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntentAppKindsTest {

    @Test
    fun getCategoryForPackage_paymentApp_returnsPaymentCategory() {
        val category = IntentAppKinds.getCategoryForPackage("com.google.android.apps.nbu.paisa.user")
        
        assertNotNull(category)
        assertEquals("payment", category.name)
    }

    @Test
    fun getCategoryForPackage_ecommerceApp_returnsEcommerceCategory() {
        val category = IntentAppKinds.getCategoryForPackage("com.amazon.mShop.android.shopping")
        
        assertNotNull(category)
        assertEquals("ecommerce and products", category.name)
    }

    @Test
    fun getCategoryForPackage_airlineApp_returnsAirTravelCategory() {
        val category = IntentAppKinds.getCategoryForPackage("com.aa.android")
        
        assertNotNull(category)
        assertEquals("air travel", category.name)
    }

    @Test
    fun getCategoryForPackage_travelBookingApp_returnsTravelCategory() {
        val category = IntentAppKinds.getCategoryForPackage("com.booking")
        
        assertNotNull(category)
        assertEquals("travel booking", category.name)
    }

    @Test
    fun getCategoryForPackage_foodOrderingApp_returnsFoodCategory() {
        val category = IntentAppKinds.getCategoryForPackage("com.ubercab.eats")
        
        assertNotNull(category)
        assertEquals("food ordering or reservations", category.name)
    }

    @Test
    fun getCategoryForPackage_unknownPackage_returnsNull() {
        val category = IntentAppKinds.getCategoryForPackage("com.unknown.package.name")
        
        assertNull(category)
    }

    @Test
    fun allCategories_hasExpectedCount() {
        val categories = IntentAppKinds.allCategories
        
        assertTrue(categories.size >= 5, "Expected at least 5 categories")
    }

    @Test
    fun allCategories_allHaveValidData() {
        IntentAppKinds.allCategories.forEach { category ->
            assertTrue(category.name.isNotBlank(), "Category name should not be blank")
            assertTrue(category.description.isNotBlank(), "Description should not be blank")
            assertTrue(category.packageNames.isNotEmpty(), "Should have at least one package")
            // generalUsageInstructions can be blank
        }
    }

    @Test
    fun allCategories_noEmptyPackageNames() {
        IntentAppKinds.allCategories.forEach { category ->
            category.packageNames.forEach { packageName ->
                assertTrue(packageName.isNotBlank(), "Package name should not be blank in ${category.name}")
            }
        }
    }

    @Test
    fun allCategories_noDuplicatePackageNames() {
        val allPackages = mutableListOf<String>()
        
        IntentAppKinds.allCategories.forEach { category ->
            allPackages.addAll(category.packageNames)
        }
        
        val uniquePackages = allPackages.distinct()
        assertEquals(allPackages.size, uniquePackages.size, "Found duplicate package names across categories")
    }

    @Test
    fun groupIntentsByCategory_emptyList_returnsEmptyOrBlankString() {
        val result = IntentAppKinds.groupIntentsByCategory(emptyList())
        
        // Result may be empty or just whitespace
        assertTrue(result.isEmpty() || result.isBlank() || result.trim().isEmpty())
    }

    @Test
    fun groupIntentsByCategory_withKnownApps_groupsCorrectly() {
        val intents = listOf(
            IntentDefinition(
                packageName = "com.booking",
                appLabel = "Booking.com",
                actions = emptyList(),
                kind = "other"
            ),
            IntentDefinition(
                packageName = "com.amazon.mShop.android.shopping",
                appLabel = "Amazon",
                actions = emptyList(),
                kind = "other"
            ),
            IntentDefinition(
                packageName = "com.unknown.app",
                appLabel = "Unknown App",
                actions = emptyList(),
                kind = "other"
            )
        )
        
        val result = IntentAppKinds.groupIntentsByCategory(intents)
        
        assertTrue(result.isNotBlank())
        // The result should contain categorized and uncategorized apps
        assertTrue(result.contains("Booking.com") || result.contains("com.booking"))
        assertTrue(result.contains("Amazon") || result.contains("com.amazon"))
        assertTrue(result.contains("Unknown App") || result.contains("com.unknown"))
    }

    @Test
    fun getCategoryForPackage_multiplePaymentApps_returnsSameCategory() {
        val category1 = IntentAppKinds.getCategoryForPackage("com.google.android.apps.nbu.paisa.user")
        val category2 = IntentAppKinds.getCategoryForPackage("net.one97.paytm")
        val category3 = IntentAppKinds.getCategoryForPackage("com.phonepe.app")
        
        assertNotNull(category1)
        assertNotNull(category2)
        assertNotNull(category3)
        assertEquals(category1.name, category2.name)
        assertEquals(category2.name, category3.name)
    }

    @Test
    fun allCategories_packageNamesFollowConvention() {
        IntentAppKinds.allCategories.forEach { category ->
            category.packageNames.forEach { packageName ->
                // Package names should follow Android convention
                assertTrue(
                    packageName.contains("."),
                    "Package name should contain dots: $packageName in ${category.name}"
                )
            }
        }
    }
}

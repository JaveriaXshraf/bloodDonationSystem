// ============================================================
//  Donor.java  –  Concrete subclass of Person
//  OOP Concepts Demonstrated:
//    - INHERITANCE          : extends Person
//    - FUNCTION OVERRIDING  : getRole(), toString(), isEligible() overloaded below
//    - FUNCTION OVERLOADING : isEligible() has two versions
//    - COMPOSITION          : Donor HAS-A ContactInfo object
//    - STATIC VARIABLE      : totalDonors
// ============================================================

public class Donor extends Person {

    // ── fields ────────────────────────────────────────────────────────────
    private String bloodGroup;
    private int    donationCount;

    // COMPOSITION – Donor owns a ContactInfo; it does NOT extend it
    private ContactInfo contactInfo;

    // STATIC VARIABLE – counts all Donor instances ever created
    private static int totalDonors = 0;

    // ── Constructor ───────────────────────────────────────────────────────
    public Donor(String name, int age, String phone,
                 String email, String bloodGroup, String city, String address) {
        super(name, age, phone, email);           // INHERITANCE – call parent
        this.bloodGroup    = bloodGroup;
        this.donationCount = 0;
        this.contactInfo   = new ContactInfo(city, address);  // COMPOSITION
        totalDonors++;
    }

    // ════════════════════════════════════════════════════════════════════
    //  FUNCTION OVERRIDING
    //  Donor provides its own implementation of the abstract method.
    //  The @Override annotation confirms this is intentional overriding.
    // ════════════════════════════════════════════════════════════════════

    @Override
    public String getRole() {
        return "Donor";   // overrides the abstract method in Person
    }

    // Overrides Object.toString() AND Person.toString() (chain of overrides)
    @Override
    public String toString() {
        return getSummary(true)            // calls Person's overloaded getSummary
               + " | Blood: " + bloodGroup
               + " | Donations: " + donationCount
               + " | " + contactInfo.getFullAddress();
    }

    // ════════════════════════════════════════════════════════════════════
    //  FUNCTION OVERLOADING
    //  isEligible() exists in two forms with different parameter lists.
    //  Same method name – different behaviour based on arguments.
    // ════════════════════════════════════════════════════════════════════

    // Version 1 – no argument: uses default age range 18–65
    public boolean isEligible() {
        return getAge() >= 18 && getAge() <= 65;
    }

    // Version 2 – custom age range passed by caller
    public boolean isEligible(int minAge, int maxAge) {
        return getAge() >= minAge && getAge() <= maxAge;
    }

    // ── Business logic ────────────────────────────────────────────────────
    public void incrementDonation() {
        donationCount++;
    }

    // ── Static method ─────────────────────────────────────────────────────
    public static int getTotalDonors() { return totalDonors; }

    // ── Getters & Setters ─────────────────────────────────────────────────
    public String getBloodGroup()                   { return bloodGroup; }
    public void   setBloodGroup(String bloodGroup)  { this.bloodGroup = bloodGroup; }

    public int  getDonationCount()                  { return donationCount; }
    public void setDonationCount(int n)             { this.donationCount = n; }

    public ContactInfo getContactInfo()              { return contactInfo; }
    public void        setContactInfo(ContactInfo c) { this.contactInfo = c; }
}

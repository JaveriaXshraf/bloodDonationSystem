// ============================================================
//  ContactInfo.java  –  Helper / Value Object
//  OOP Concept: COMPOSITION – used inside Donor (Donor HAS-A ContactInfo)
//  This class is a simple data holder; it does NOT extend Person.
// ============================================================

public class ContactInfo {

    private String city;
    private String address;

    // ---------- constructor ----------
    public ContactInfo(String city, String address) {
        this.city    = city;
        this.address = address;
    }

    // ---------- behaviour ----------
    public String getFullAddress() {
        return address + ", " + city;
    }

    // ---------- getters & setters ----------
    public String getCity()               { return city; }
    public void   setCity(String city)    { this.city = city; }

    public String getAddress()                { return address; }
    public void   setAddress(String address)  { this.address = address; }

    @Override
    public String toString() {
        return getFullAddress();
    }
}

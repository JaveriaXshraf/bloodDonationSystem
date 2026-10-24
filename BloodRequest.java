// ============================================================
//  BloodRequest.java  –  Represents a blood request by a patient
//  OOP Concepts:
//    - ENCAPSULATION   : all fields private, accessed via getters/setters
//    - STATIC VARIABLE : totalRequests counter
//    - STATIC METHOD   : getTotalRequests()
//    - FUNCTION OVERLOADING : getDetails() in two versions
// ============================================================

public class BloodRequest {

    // ── Fields ────────────────────────────────────────────────────────────
    private int    id;              // auto-assigned by MySQL
    private String patientName;
    private String bloodGroupNeeded;
    private String hospital;
    private String contactPhone;
    private String urgency;         // "Normal" or "Urgent"
    private String status;          // "Pending", "Matched", "Fulfilled"

    // ── STATIC VARIABLE – tracks how many requests created this session ───
    private static int totalRequests = 0;

    // ── Constructor ───────────────────────────────────────────────────────
    public BloodRequest(String patientName, String bloodGroupNeeded,
                        String hospital, String contactPhone, String urgency) {
        this.patientName      = patientName;
        this.bloodGroupNeeded = bloodGroupNeeded;
        this.hospital         = hospital;
        this.contactPhone     = contactPhone;
        this.urgency          = urgency;
        this.status           = "Pending";   // every new request starts as Pending
        totalRequests++;
    }

    // ════════════════════════════════════════════════════════════════════
    //  FUNCTION OVERLOADING – getDetails() in two versions
    // ════════════════════════════════════════════════════════════════════

    // Version 1 – short summary (used in table/list views)
    public String getDetails() {
        return "[" + urgency + "] " + patientName
               + " needs " + bloodGroupNeeded
               + " | Status: " + status;
    }

    // Version 2 – full details including hospital and phone
    public String getDetails(boolean full) {
        if (!full) return getDetails();
        return getDetails()
               + " | Hospital: " + hospital
               + " | Phone: "    + contactPhone;
    }

    // ── STATIC METHOD ─────────────────────────────────────────────────────
    public static int getTotalRequests() { return totalRequests; }

    // ── Getters & Setters ─────────────────────────────────────────────────
    public int    getId()                         { return id; }
    public void   setId(int id)                   { this.id = id; }

    public String getPatientName()                { return patientName; }
    public void   setPatientName(String n)        { this.patientName = n; }

    public String getBloodGroupNeeded()           { return bloodGroupNeeded; }
    public void   setBloodGroupNeeded(String b)   { this.bloodGroupNeeded = b; }

    public String getHospital()                   { return hospital; }
    public void   setHospital(String h)           { this.hospital = h; }

    public String getContactPhone()               { return contactPhone; }
    public void   setContactPhone(String p)       { this.contactPhone = p; }

    public String getUrgency()                    { return urgency; }
    public void   setUrgency(String u)            { this.urgency = u; }

    public String getStatus()                     { return status; }
    public void   setStatus(String s)             { this.status = s; }

    @Override
    public String toString() { return getDetails(true); }
}

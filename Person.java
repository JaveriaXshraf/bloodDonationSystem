// ============================================================
//  Person.java  –  Abstract Base Class
//  OOP Concepts Demonstrated:
//    - ABSTRACTION      : class is abstract; getRole() is abstract
//    - ENCAPSULATION    : all fields private, accessed via getters/setters
//    - STATIC VARIABLE  : totalPersonsCreated shared across all instances
//    - STATIC METHOD    : getTotalPersonsCreated()
//    - FUNCTION OVERLOADING : getSummary() has two versions (same name, different params)
//    - FUNCTION OVERRIDING  : toString() overrides Object.toString()
// ============================================================

public abstract class Person {

    // ── private fields (ENCAPSULATION) ───────────────────────────────────
    private String name;
    private int    age;
    private String phone;
    private String email;

    // ── STATIC VARIABLE – one copy shared by ALL Person objects ──────────
    private static int totalPersonsCreated = 0;

    // ── Constructor ───────────────────────────────────────────────────────
    public Person(String name, int age, String phone, String email) {
        this.name  = name;
        this.age   = age;
        this.phone = phone;
        this.email = email;
        totalPersonsCreated++;   // increments every time any Person is made
    }

    // ── ABSTRACTION – subclass MUST implement this ────────────────────────
    public abstract String getRole();

    // ════════════════════════════════════════════════════════════════════
    //  FUNCTION OVERLOADING
    //  Both methods are named getSummary() but accept different parameters.
    //  Java picks the right one based on what you pass at the call site.
    // ════════════════════════════════════════════════════════════════════

    // Version 1 – no argument: returns a short one-line summary
    public String getSummary() {
        return "[" + getRole() + "] " + name + " | Age: " + age + " | Phone: " + phone;
    }

    // Version 2 – boolean argument: optionally includes email in the summary
    public String getSummary(boolean includeEmail) {
        String base = getSummary();               // reuse version 1
        return includeEmail ? base + " | Email: " + email : base;
    }

    // ── STATIC METHOD ─────────────────────────────────────────────────────
    public static int getTotalPersonsCreated() {
        return totalPersonsCreated;
    }

    // ── FUNCTION OVERRIDING – overrides Object.toString() ─────────────────
    @Override
    public String toString() {
        return getSummary();   // delegates to overloaded version 1
    }

    // ── Getters & Setters ─────────────────────────────────────────────────
    public String getName()              { return name; }
    public void   setName(String name)   { this.name = name; }

    public int  getAge()                 { return age; }
    public void setAge(int age)          { this.age = age; }

    public String getPhone()             { return phone; }
    public void   setPhone(String phone) { this.phone = phone; }

    public String getEmail()             { return email; }
    public void   setEmail(String email) { this.email = email; }
}

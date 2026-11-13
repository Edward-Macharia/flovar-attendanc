# Frappe Cloud > Server Script > Script Type: API
# API Method: flovar_checkin   | Allow Guest: OFF  | Enabled: ON
# Endpoint: POST /api/method/flovar_checkin
fd = frappe.form_dict

log_type = fd.get("log_type")
if log_type not in ("IN", "OUT"):
    frappe.throw("Invalid log type")

try:
    lat = float(fd.get("latitude"))
    lng = float(fd.get("longitude"))
    acc = float(fd.get("accuracy") or 99999)
except Exception:
    frappe.throw("Location is required to record attendance")

if str(fd.get("is_mock")) in ("1", "true", "True"):
    frappe.throw("Mock location detected. Attendance not recorded.")
if acc > 100:
    frappe.throw("GPS accuracy too low (" + str(int(acc)) + " m). Retry with a better signal.")
if abs(lat) > 90 or abs(lng) > 180:
    frappe.throw("Invalid coordinates")

# Employee is derived from the logged-in session, never from client input
emp = frappe.db.get_value(
    "Employee", {"user_id": frappe.session.user, "status": "Active"},
    ["name", "employee_name", "default_shift"], as_dict=True)
if not emp:
    frappe.throw("No active Employee record is linked to your user.")

# Duplicate / sequence guard
last = frappe.get_all("Employee Checkin", filters={"employee": emp.name},
                      fields=["log_type"], order_by="time desc", limit=1)
if last and last[0].log_type == log_type:
    frappe.throw("You are already clocked " + log_type.lower() + ".")

# Geofence against the Shift Location of the employee's default shift (if one is set)
site_name = None
try:
    if emp.default_shift:
        site_name = frappe.db.get_value("Shift Type", emp.default_shift, "shift_location")
except Exception:
    site_name = None

if site_name:
    site = frappe.db.get_value("Shift Location", site_name,
                               ["latitude", "longitude", "checkin_radius"], as_dict=True)
    if site and site.latitude and site.longitude:
        # equirectangular approximation (accurate to <1 m at these radii); no imports needed in the sandbox
        x = (lat * 3.141592653589793 / 180.0)
        cosx = 1 - x * x / 2 + x * x * x * x / 24
        dy = (lat - float(site.latitude)) * 111320.0
        dx = (lng - float(site.longitude)) * 111320.0 * cosx
        dist = (dx * dx + dy * dy) ** 0.5
        radius = float(site.checkin_radius or 100)
        if dist > radius + acc / 2:
            frappe.throw("You are " + str(int(dist)) + " m from " + site_name +
                         ". Move within " + str(int(radius)) + " m to clock " + log_type.lower() + ".")

doc = frappe.get_doc({
    "doctype": "Employee Checkin",
    "employee": emp.name,
    "log_type": log_type,
    "time": frappe.utils.now_datetime(),   # server time, never client time
    "latitude": lat,
    "longitude": lng,
    "device_id": (fd.get("device_id") or "")[:140],
})
doc.insert(ignore_permissions=True)

frappe.response["message"] = {"message": "Clocked " + log_type.lower() + " at " +
                              frappe.utils.nowtime()[:5], "name": doc.name}

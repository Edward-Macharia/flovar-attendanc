# Frappe Cloud > Server Script > Script Type: API
# API Method: flovar_today   | Allow Guest: OFF  | Enabled: ON
# Endpoint: GET /api/method/flovar_today
emp = frappe.db.get_value(
    "Employee", {"user_id": frappe.session.user, "status": "Active"},
    ["name", "employee_name", "default_shift"], as_dict=True)
if not emp:
    frappe.throw("No active Employee record is linked to your user.")

logs = frappe.get_all("Employee Checkin",
                      filters={"employee": emp.name, "time": [">=", frappe.utils.today()]},
                      fields=["log_type", "time"], order_by="time asc")
site = None
try:
    if emp.default_shift:
        site = frappe.db.get_value("Shift Type", emp.default_shift, "shift_location")
except Exception:
    site = None

frappe.response["message"] = {
    "employee_name": emp.employee_name,
    "site": site,
    "logs": [{"log_type": l.log_type, "time": str(l.time)[11:16]} for l in logs],
}

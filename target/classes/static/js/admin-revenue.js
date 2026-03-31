document.addEventListener("DOMContentLoaded",()=>{
  // Read role from sessionStorage (per-tab) — localStorage is shared across
  // all tabs and would be overwritten if a different user logs in on another tab.
  const role = sessionStorage.getItem("role");

if(role !== 'admin'){
alert("Admins Only");
window.location.replace("/");
return;
}
loadRevenue();
});

async function loadRevenue(){
try{
const events =
await apiRequest("/api/revenue","GET",null,true);

const container =
document.getElementById(
"revenue-list"
);

container.innerHTML="";
events.forEach(event=>{

let soldTickets = 0;
let ticketRevenue = 0;
let convenienceRevenue = 0;
let gstCollected = 0;
let totalCollection = 0;

if(event.Bookings){
event.Bookings.forEach(b=>{

soldTickets +=
b.tickets_booked;

ticketRevenue +=
b.ticket_amount;

convenienceRevenue +=
b.convenience_fee;

gstCollected +=
b.gst_amount;

totalCollection +=
b.total_paid;

});

}

const div =
document.createElement("div");

div.innerHTML = `

<h2>

${event.title}

</h2>

Tickets Sold :

${soldTickets}

<br/>

Ticket Revenue :

₹${ticketRevenue.toFixed(2)}

<br/>

Convenience Fee Revenue :

₹${convenienceRevenue.toFixed(2)}

<br/>

GST Collected :

₹${gstCollected.toFixed(2)}

<br/>

Total Collection :

₹${totalCollection.toFixed(2)}

<hr/>
`;
container.appendChild(div);

});

}

catch(err){
alert(
"Failed loading revenue"
);
}
}

function goBack(){
window.location.href="/admin";
}

function logout(){
  // Read userId from sessionStorage (per-tab) so the broadcast targets only
  // this user's tabs — not a different user who may be logged in on another tab.
  const userId = sessionStorage.getItem('userId');
  if (window._authChannel && userId) {
    window._authChannel.postMessage({ type: 'LOGOUT', userId });
  }
  fetch("/auth/logout", { method: "POST", credentials: "include" })
    .finally(() => {
      sessionStorage.clear();
      window.location.replace("/");
    });
}



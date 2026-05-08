/* seat-selection.js  — Features 3 (tiered categories) + 4 (seat hold timer) */
const CATEGORY_COLORS={Silver:{bg:'rgba(176,190,197,0.15)',border:'rgba(176,190,197,0.5)',text:'#b0bec5',label:'⬜ Silver'},Gold:{bg:'rgba(245,200,66,0.15)',border:'rgba(245,200,66,0.55)',text:'#f5c842',label:'🟡 Gold'},Platinum:{bg:'rgba(124,106,247,0.15)',border:'rgba(124,106,247,0.55)',text:'#a89cf7',label:'🟣 Platinum'},Recliner:{bg:'rgba(66,230,149,0.13)',border:'rgba(66,230,149,0.5)',text:'#42e695',label:'🟢 Recliner'},Wheelchair:{bg:'rgba(79,195,247,0.13)',border:'rgba(79,195,247,0.5)',text:'#4fc3f7',label:'♿ Wheelchair'},default:{bg:'rgba(124,106,247,0.12)',border:'rgba(124,106,247,0.4)',text:'#b8b0ff',label:'🔷 Standard'}};
const SEL_C={bg:'#7c6af7',border:'#7c6af7',text:'#fff'},HLD_C={bg:'rgba(255,159,67,0.2)',border:'rgba(255,159,67,0.6)',text:'#ff9f43'};
let selectedSeats=[],requiredCount=0,currentEventId=null,seatPriceMap={},catPriceMap={};

document.addEventListener("DOMContentLoaded",async()=>{
  try{const s=await apiRequest("/auth/me","GET");sessionStorage.setItem("userId",String(s.userId));}catch{return;}
  const raw=sessionStorage.getItem("seat_selection_meta");
  if(!raw){alert("No booking session found.");window.location.replace("/events-page");return;}
  const meta=JSON.parse(raw);currentEventId=meta.event_id;requiredCount=meta.tickets_booked;
  document.getElementById("required-count").textContent=requiredCount;
  document.getElementById("seat-info").textContent=`Please select ${requiredCount} seat(s)`;
  document.getElementById("proceed-btn").addEventListener("click",proceedToPayment);
  await loadSeats(currentEventId);
});

async function loadSeats(eventId){
  try{
    const seats=await apiRequest(`/seats/${eventId}`,"GET",null,true);
    if(!seats||!seats.length){document.getElementById("seat-grid").innerHTML="<p style='color:var(--muted)'>No seats found.</p>";return;}
    seatPriceMap={};catPriceMap={};
    seats.forEach(s=>{if(s.price!=null)seatPriceMap[s.seat_number]=s.price;const c=s.category||'default';if(s.price!=null&&!catPriceMap[c])catPriceMap[c]=s.price;});
    renderCategoryLegend(seats);
    const rows={};seats.forEach(s=>{const r=s.seat_number[0];if(!rows[r])rows[r]=[];rows[r].push(s);});
    const grid=document.getElementById("seat-grid");grid.innerHTML="";
    Object.keys(rows).sort().forEach(rowLabel=>{
      const rowDiv=document.createElement("div");rowDiv.className="seat-row";
      const lbl=document.createElement("span");lbl.className="row-label";lbl.textContent=rowLabel;rowDiv.appendChild(lbl);
      rows[rowLabel].forEach(seat=>{
        const btn=document.createElement("button"),isBooked=seat.status==='booked',isHeld=seat.status==='held',cat=seat.category||'default',col=CATEGORY_COLORS[cat]||CATEGORY_COLORS.default;
        btn.dataset.seat=seat.seat_number;btn.dataset.category=cat;btn.dataset.price=seat.price||'';btn.disabled=isBooked||isHeld;
        if(isBooked){btn.className="seat booked";}
        else if(isHeld){btn.className="seat held";btn.style.background=HLD_C.bg;btn.style.borderColor=HLD_C.border;btn.style.color=HLD_C.text;btn.title="Held by another user";}
        else{btn.className="seat available";btn.style.background=col.bg;btn.style.borderColor=col.border;btn.style.color=col.text;}
        btn.innerHTML=seat.price!=null?`<span style="display:block;font-size:0.6rem;line-height:1">${seat.seat_number}</span><span style="display:block;font-size:0.58rem;opacity:0.8">₹${seat.price}</span>`:seat.seat_number;
        if(!isBooked&&!isHeld)btn.addEventListener("click",()=>toggleSeat(btn,seat.seat_number,cat,seat.price));
        rowDiv.appendChild(btn);
      });
      grid.appendChild(rowDiv);
    });
  }catch(err){document.getElementById("seat-grid").innerHTML=`<p style="color:#f74a6a">Error: ${err.message}</p>`;}
}

function renderCategoryLegend(seats){
  const legend=document.querySelector('.legend');if(!legend)return;
  legend.innerHTML=`<div class="legend-item"><div class="legend-box available"></div> Available</div><div class="legend-item"><div class="legend-box selected"></div> Selected</div><div class="legend-item"><div class="legend-box booked"></div> Booked</div><div class="legend-item"><div class="legend-box" style="background:rgba(255,159,67,0.2);border:2px solid rgba(255,159,67,0.6);"></div> Held</div>`;
  const cats=[...new Set(seats.map(s=>s.category||'default'))].filter(c=>c!=='default');
  cats.forEach(cat=>{const col=CATEGORY_COLORS[cat]||CATEGORY_COLORS.default,price=catPriceMap[cat],item=document.createElement('div');item.className='legend-item';item.innerHTML=`<div class="legend-box" style="background:${col.bg};border:2px solid ${col.border};"></div><span style="color:${col.text};">${col.label}${price!=null?` — ₹${price}`:''}</span>`;legend.appendChild(item);});
}

function toggleSeat(btn,seatNumber,cat,price){
  const isSel=selectedSeats.includes(seatNumber),col=CATEGORY_COLORS[cat]||CATEGORY_COLORS.default;
  if(isSel){selectedSeats=selectedSeats.filter(s=>s!==seatNumber);btn.style.background=col.bg;btn.style.borderColor=col.border;btn.style.color=col.text;btn.style.transform='';btn.style.boxShadow='';btn.classList.remove("selected");btn.classList.add("available");}
  else{if(selectedSeats.length>=requiredCount){alert(`You can only select ${requiredCount} seat(s).`);return;}selectedSeats.push(seatNumber);btn.style.background=SEL_C.bg;btn.style.borderColor=SEL_C.border;btn.style.color=SEL_C.text;btn.style.transform='scale(1.05)';btn.style.boxShadow='0 0 14px rgba(124,106,247,0.45)';btn.classList.remove("available");btn.classList.add("selected");}
  document.getElementById("selected-count").textContent=selectedSeats.length;
  document.getElementById("proceed-btn").disabled=selectedSeats.length!==requiredCount;
  updatePriceTotal();
}

function updatePriceTotal(){
  const el=document.getElementById("price-total");if(!el)return;
  const has=selectedSeats.some(s=>seatPriceMap[s]!=null);
  if(!has){el.textContent='';return;}
  const total=selectedSeats.reduce((sum,s)=>sum+(seatPriceMap[s]||0),0);
  el.textContent=`Seat total: ₹${total.toFixed(0)}`;
}

async function proceedToPayment(){
  if(selectedSeats.length!==requiredCount){alert(`Please select exactly ${requiredCount} seat(s)`);return;}
  const btn=document.getElementById("proceed-btn");btn.disabled=true;btn.textContent="Holding seats…";
  try{
    await apiRequest(`/seats/${currentEventId}/hold`,"POST",{seatNumbers:selectedSeats},true);
  }catch(holdErr){
    alert("Could not hold seats: "+holdErr.message+"\nThe seats may have been taken. Please choose different seats.");
    btn.disabled=false;btn.textContent="Proceed to Payment →";await loadSeats(currentEventId);return;
  }
  btn.textContent="Creating order…";
  try{
    const meta=JSON.parse(sessionStorage.getItem("seat_selection_meta"));
    const data=await apiRequest("/payments/create-order","POST",{event_id:meta.event_id,tickets_booked:meta.tickets_booked,selected_seats:selectedSeats},true);
    sessionStorage.setItem("razorpay_order",JSON.stringify(data));sessionStorage.removeItem("seat_selection_meta");window.location.href="/payment";
  }catch(err){alert("Could not initiate payment: "+err.message);btn.disabled=false;btn.textContent="Proceed to Payment →";}
}

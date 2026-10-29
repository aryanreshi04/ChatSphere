'use strict';
const map = L.map('map', {zoomControl:true}).setView([0,0],2);
const destinations = L.layerGroup().addTo(map);
let tiles=null, dot=null, accuracy=null, chosen=null, centered=false, previousTarget='';
const status=document.getElementById('status');
window.renderMap=function(config){
  if(config.tiles && !tiles){
    status.textContent='Loading map…';
    tiles=L.tileLayer(config.tileUrl,{maxZoom:19,keepBuffer:0,updateWhenIdle:true,
      attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'}).addTo(map);
    tiles.on('load',()=>{status.textContent='Tap a place for walking directions. Map data may be incomplete.';});
    tiles.on('tileerror',()=>{status.textContent='Some map tiles are unavailable. Check connection or retry online maps.';});
  } else if(!config.tiles && tiles){map.removeLayer(tiles);tiles=null;status.textContent='Map tiles are off.';}
  destinations.clearLayers();
  config.destinations.forEach(place=>{
    L.circleMarker([place.latitude,place.longitude],{radius:8,color:place.verified?'#673ab7':'#777'}).addTo(destinations)
      .bindTooltip(document.createTextNode(place.name+(place.verified?'':' (unverified example)')));
  });
  if(config.location){
    const pos=[config.location.latitude,config.location.longitude];
    if(!dot){dot=L.circleMarker(pos,{radius:8,color:'#fff',weight:3,fillColor:'#1976d2',fillOpacity:1}).addTo(map);
      accuracy=L.circle(pos,{radius:config.location.accuracy,color:'#1976d2',weight:1,fillOpacity:0.12}).addTo(map);}
    dot.setLatLng(pos);accuracy.setLatLng(pos).setRadius(config.location.accuracy);
    if(config.follow||!centered){map.setView(pos,centered?map.getZoom():16);centered=true;}
  }
  if(config.target && config.target.name!==previousTarget){
    previousTarget=config.target.name;map.setView([config.target.latitude,config.target.longitude],17);
  }
  map.invalidateSize();
};
map.on('click',event=>{
  if(chosen)map.removeLayer(chosen);
  const lat=event.latlng.lat,lon=((event.latlng.lng+180)%360+360)%360-180;
  const box=document.createElement('div');
  const label=document.createElement('p');label.textContent='Selected point: '+lat.toFixed(5)+', '+lon.toFixed(5);box.appendChild(label);
  const link=document.createElement('a');link.textContent='Walking directions in Google Maps';
  link.href='https://www.google.com/maps/dir/?api=1&travelmode=walking&destination='+lat+','+lon;box.appendChild(link);
  chosen=L.circleMarker([lat,lon],{radius:7,color:'#673ab7'}).addTo(map).bindPopup(box).openPopup();
});

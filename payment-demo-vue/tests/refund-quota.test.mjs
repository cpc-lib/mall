import assert from 'node:assert/strict'
const OCCUPIED = ['PENDING','ACCEPTED']
function available(item, apps, exclude='') { const occ=apps.filter(a=>OCCUPIED.includes(a.apply.applyStatus)&&a.apply.refundNo!==exclude).reduce((s,a)=>s+Number((a.items.find(i=>i.orderItemId===item.id)||{}).refundQuantity||0),0); const succ=apps.filter(a=>a.apply.applyStatus==='SUCCESS'&&a.apply.refundNo!==exclude).reduce((s,a)=>s+Number((a.items.find(i=>i.orderItemId===item.id)||{}).refundQuantity||0),0); return Math.max(0,item.quantity-Math.max(item.refundedQuantity||0,succ)-occ) }
const item={id:11,quantity:10,refundedQuantity:2}; const apps=[{apply:{refundNo:'R1',applyStatus:'PENDING'},items:[{orderItemId:11,refundQuantity:3}]},{apply:{refundNo:'R2',applyStatus:'ACCEPTED'},items:[{orderItemId:11,refundQuantity:1}]}]
assert.equal(available(item,apps),4); assert.equal(available(item,apps,'R1'),7); console.log('vue refund-quota: PASS')

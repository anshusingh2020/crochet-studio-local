export function isAdminEmail(email:string|undefined,configured:string|undefined){return !!email&&!!configured&&email.toLowerCase()===configured.toLowerCase();}
export function canAccessOrder(userId:string,admin:boolean,ownerId:string){return admin||userId===ownerId;}
export function canTransition(current:string,next:string){const transitions:Record<string,string[]>={requested:["cancelled"],quoted:["cancelled"],accepted:["paid","cancelled"],paid:["making"],making:["shipped"],shipped:["delivered"]};return (transitions[current]||[]).includes(next);}

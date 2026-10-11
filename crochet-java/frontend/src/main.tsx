import {createRoot} from 'react-dom/client';
import {useEffect,useState} from 'react';
import Studio from './Studio';import Admin from './Admin';import Account from './Account';import Requests from './Requests';import Login from './Login';import Policies from './Policies';import './styles.css';
function App(){const [session,setSession]=useState<any>(null),[error,setError]=useState('');useEffect(()=>{if(location.pathname==='/logout'){fetch('/api/auth',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'logout'})}).then(r=>{if(!r.ok)throw Error('Could not sign out');location.assign('/login')}).catch(e=>setError(e.message));return;}fetch('/api/auth/me',{cache:'no-store'}).then(async r=>{if(!r.ok)throw Error('Could not connect to the studio.');setSession(await r.json())}).catch(e=>setError(e.message));},[]);const path=location.pathname;
if(path==='/login'||path==='/admin/login')return <Login admin={path.startsWith('/admin')}/>;
if(error)return <main className="dashboard"><h1>Connection unavailable</h1><p>{error}</p><button className="primary" onClick={()=>location.reload()}>Try again</button></main>;
if(!session)return <main className="dashboard">Loading your studio…</main>;
if(path==='/policies')return <Policies/>;
if(path==='/admin/requests')return session.owner?<Requests/>:<Gate message="Only the super admin can review access requests."/>;
if(path==='/admin')return session.admin?<Admin owner={session.owner}/>:<Gate message="Sign in with an approved admin or super admin account."/>;
if(path==='/account')return session.signedIn?<Account/>:<Gate message="Sign in to manage your delivery details."/>;
return <Studio signedIn={session.signedIn}/>;
}
function Gate({message}:{message:string}){return <main className="dashboard"><h1>Sign in required</h1><p>{message}</p><a className="primary" href={location.pathname.startsWith('/admin')?'/admin/login':'/login'}>Sign in</a><p><a href="/">Back to the shop</a></p></main>}
createRoot(document.getElementById('root')!).render(<App/>);

import AssemblyDashboard from './AssemblyDashboard'
import CommunicationDashboard from './CommunicationDashboard'

export default function Dashboard() {
  return (
    <div className='flex flex-col w-full h-full items-center justify-center'>
      <AssemblyDashboard />
      <CommunicationDashboard />
    </div>
  )
}

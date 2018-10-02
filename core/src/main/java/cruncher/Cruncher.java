package cruncher;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DBObjects.JEXWorkflow;
import Database.DataReader.ImageReader;
import jex.statics.JEXStatics;
import logs.Logs;
import miscellaneous.Canceler;
import miscellaneous.FileUtility;
import miscellaneous.Pair;
import miscellaneous.StringUtility;
import tables.DimTable;

/**
 * Note that Canceler only refers to GUI Tasks for now.
 * @author jay_warrick
 *
 */
public class Cruncher implements Canceler {
	
	public volatile boolean stopCrunch = false;
	public volatile boolean stopGuiTask = false;
	
	public static int STANDARD_NUM_THREADS = 4;
	
	public BatchPanelList batchList = new BatchPanelList();
	
	List<Callable<Integer>> guiTasks;
	Vector<Pair<String,Vector<Ticket>>> tickets; // Tickets
	// organized
	// by
	// Workflow
	// and
	// entry
	public final ExecutorService guiTicketQueue = Executors.newFixedThreadPool(1);
	private final ExecutorService ticketQueue = Executors.newFixedThreadPool(1);
	private ExecutorService multiFunctionQueue = Executors.newFixedThreadPool(5);
	private ExecutorService singleFunctionQueue = Executors.newFixedThreadPool(1);
	
	private JEXWorkflow workflowToUpdate = new JEXWorkflow("Workflow Updater");
	private TreeSet<JEXEntry> entriesToUpdate = new TreeSet<>();
	private boolean updateAutoSaving = false;
	private ExecutorService updaterService = Executors.newFixedThreadPool(1);
	private Future<?> updaterFuture = null;
	private DirectoryWatcher directoryWatcher = null;
	private String dirToWatch = null;
	private DimTable template = null;
	private String timeToken = null;
	
	public Cruncher()
	{
		this.directoryWatcher = new DirectoryWatcher(this);
		this.initUpdater();
		this.tickets = new Vector<Pair<String,Vector<Ticket>>>(0);
		this.guiTasks = new Vector<Callable<Integer>>(0);
	}
	
	public BatchPanelList getBatchViewer()
	{
		return this.batchList;
	}
	
	public void runUpdate()
	{
		Logs.log("Running Update." + this.updateAutoSaving, this);
		Logs.log("" + template, this);
		//this.runWorkflow(this.workflowToUpdate, this.entriesToUpdate, this.updateAutoSaving, false);
	}
	
	public void runWorkflow(JEXWorkflow workflow, TreeSet<JEXEntry> entries, boolean autoSave, boolean autoUpdate)
	{
		// Save info in case we need to run this again during updates
		if(autoUpdate && workflow.get(0).getFunctionName().equals("Import Virtual Image Updates"))
		{
			this.workflowToUpdate.addAll(workflow);
			this.entriesToUpdate.addAll(entries);
			this.updateAutoSaving = autoSave;
		}
		
		Batch batch = new Batch();
		boolean first = true;
		for (JEXFunction function : workflow)
		{
			// If autoUpdating is on, the first function in the workflow is the ImportVirtualImageUpdates function, then start the updater.
			if(autoUpdate && first && function.getFunctionName().equals("Import Virtual Image Updates"))
			{
				// Grab the first non-null data
				JEXData input = null;
				for(JEXEntry entry : entries)
				{
					if(input == null)
					{
						input = JEXStatics.jexManager.getDataOfTypeNameInEntry(function.inputs.firstEntry().getValue(), entry);
					}
					else
					{
						break;
					}
				}
				// If the data is virtual
				if(input != null && input.hasVirtualFlavor())
				{
					String path = ImageReader.readObjectToImagePath(input);
					String tmp = function.parameters.getParameter("Time String Token").getValue();
					if(!tmp.equals(""))
					{
						this.dirToWatch = FileUtility.getFileParent(path);
						this.timeToken = StringUtility.removeWhiteSpaceOnEnds(tmp);
						this.template = input.getDimTable();
						try
						{
							this.directoryWatcher.watch(dirToWatch);
						}
						catch (IOException e)
						{
							e.printStackTrace();
						}
					}
				}
			}
			
			// Then create a ticket for this function and set of entries.
			Ticket ticket = new Ticket(function, entries, autoSave);
			batch.add(ticket);
		}
		this.batchList.add(batch);
		
		for (Ticket ticket : batch)
		{
			this.runTicket(ticket);
		}
	}
	
	private void initUpdater()
    {
		if(this.updaterFuture == null)
		{
			this.updaterFuture = this.updaterService.submit(this.directoryWatcher);
		}   
    }
	
	public void stopUpdater()
	{
		// abort watcher
        this.updaterFuture.cancel(true);
        this.updaterFuture = null;
        this.directoryWatcher.reset();
        this.initUpdater();
	}
	
	public void runTicket(Ticket ticket)
	{
		Logs.log("Added ticket to running queue ", 1, this);
		JEXStatics.statusBar.setStatusText("Added ticket to running queue ");
		this.ticketQueue.submit(ticket);
	}
	
	public void setNumThreads(Integer numThreads)
	{
		this.multiFunctionQueue.shutdown();
		if(numThreads == null)
		{
			this.multiFunctionQueue = Executors.newFixedThreadPool(STANDARD_NUM_THREADS);
		}
		else
		{
			this.multiFunctionQueue = Executors.newFixedThreadPool(numThreads);
		}
	}
	
	public Future<Integer> runFunction(FunctionCallable function)
	{
		Logs.log("Added function to cruncher queue ", 1, this);
		JEXStatics.statusBar.setStatusText("Added function to cruncher queue ");
		Future<Integer> result = null;
		if(function.getFunctionObject().getCrunch().allowMultithreading())
		{
			result = this.multiFunctionQueue.submit(function);
		}
		else
		{
			result = this.singleFunctionQueue.submit(function);
		}
		return result;
	}
	
	public synchronized void finishTicket(Ticket ticket)
	{
		String str = "Crunch canceled, failed, or created no objects. No changes made.";
		if(ticket == null)
		{
			JEXStatics.statusBar.setStatusText(str);
			Logs.log(str, 0, this);
			return;
		}
		TreeMap<JEXEntry,Set<JEXData>> outputList = ticket.outputList;
		if(outputList == null || outputList.size() == 0)
		{
			JEXStatics.statusBar.setStatusText(str);
			Logs.log(str, 0, this);
			return;
		}
		JEXStatics.statusBar.setStatusText("Function successful. Creating output objects.");
		Logs.log("Function successful. Creating output objects.", 0, this);
		JEXStatics.jexDBManager.saveDataListInEntries(outputList, true);
		
		if(ticket.getAutoSave())
		{
			JEXStatics.main.save();
		}
	}
	
	public Future<Object> runGuiTask(Callable<Object> guiTask)
	{
		this.stopGuiTask = false;
		Logs.log("Added GUI task to running queue. Use Ctrl(CMD)+G to cancel the current task. ", 1, this);
		JEXStatics.statusBar.setStatusText("Added GUI task to running queue. Use Ctrl(CMD)+G to cancel the current task. ");
		return this.guiTicketQueue.submit(guiTask);
	}
	
	public synchronized void finishImportThread(ImportThread importThread)
	{
		String str = "Import canceled, failed, or no objects created. No changes made.";
		boolean successful = false;
		if(importThread == null)
		{
			JEXStatics.statusBar.setStatusText(str);
			Logs.log(str, 0, this);
			return;
		}
		TreeMap<JEXEntry,JEXData> toAdd = importThread.toAdd;
		if(toAdd == null || toAdd.size() == 0)
		{
			JEXStatics.statusBar.setStatusText(str);
			Logs.log(str, 0, this);
			return;
		}
		successful = JEXStatics.jexDBManager.saveDataInEntries(toAdd);
		if(successful)
		{
			JEXStatics.statusBar.setStatusText("Objects imported successfully");
			JEXStatics.jexManager.saveCurrentDatabase();
		}
		else
		{
			JEXStatics.statusBar.setStatusText("Import failed or created no objects. No changes made.");
			Logs.log(str, 0, this);
		}
	}
	
	public synchronized void finishExportThread(ExportThread exportThread)
	{
		String str = "Export canceled, failed, or no objects created. No changes made.";
		if(exportThread == null)
		{
			JEXStatics.statusBar.setStatusText(str);
			Logs.log(str, 0, this);
			return;
		}
		JEXStatics.statusBar.setStatusText("Objects exported successfully");
	}

	@Override
	public boolean isCanceled() {
		return this.stopGuiTask;
	}
	
}

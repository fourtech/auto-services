/* Implements ProcessesMonitor */
#include <ctype.h>
#include <dirent.h>
#include <grp.h>
#include <inttypes.h>
#include <pwd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

#include "ProcessesMonitor.h"

void init_procs(void) {
	free_procs = NULL;
	num_new_procs = num_old_procs = 0;
	new_procs = old_procs = NULL;
	read_procs();
}

int read_procs_out(struct proc_info **old_procs_out, struct proc_info **new_procs_out) {
	old_procs = new_procs;
	num_old_procs = num_new_procs;
	memcpy(&old_cpu, &new_cpu, sizeof(old_cpu));
	read_procs();

	// copy to out
	memcpy(&old_procs_out, &old_procs, sizeof(old_procs));
	memcpy(&new_procs_out, &new_procs, sizeof(new_procs));
	return num_new_procs;
}

static struct proc_info *alloc_proc(void) {
	struct proc_info *proc;

	if (free_procs) {
		proc = free_procs;
		free_procs = free_procs->next;
		num_free_procs--;
	} else {
		proc = malloc(sizeof(*proc));
		if (!proc) die("Could not allocate struct process_info.\n");
	}

	num_used_procs++;

	return proc;
}

static void free_proc(struct proc_info *proc) {
	proc->next = free_procs;
	free_procs = proc;

	num_used_procs--;
	num_free_procs++;
}

#define MAX_LINE 256

static void read_procs(void) {
	DIR *proc_dir, *task_dir;
	struct dirent *pid_dir, *tid_dir;
	char filename[64];
	FILE *file;
	int proc_num;
	struct proc_info *proc;
	pid_t pid, tid;

	int i;

	proc_dir = opendir("/proc");
	if (!proc_dir) die("Could not open /proc.\n");

	new_procs = calloc(INIT_PROCS * (threads ? THREAD_MULT : 1), sizeof(struct proc_info *));
	num_new_procs = INIT_PROCS * (threads ? THREAD_MULT : 1);

	file = fopen("/proc/stat", "r");
	if (!file) die("Could not open /proc/stat.\n");
	fscanf(file, "cpu  %lu %lu %lu %lu %lu %lu %lu", &new_cpu.utime, &new_cpu.ntime, &new_cpu.stime,
			&new_cpu.itime, &new_cpu.iowtime, &new_cpu.irqtime, &new_cpu.sirqtime);
	fclose(file);

	proc_num = 0;
	while ((pid_dir = readdir(proc_dir))) {
		if (!isdigit(pid_dir->d_name[0]))
			continue;

		pid = atoi(pid_dir->d_name);

		struct proc_info cur_proc;

		if (!threads) {
			proc = alloc_proc();

			proc->pid = proc->tid = pid;

			sprintf(filename, "/proc/%d/stat", pid);
			read_stat(filename, proc);

			sprintf(filename, "/proc/%d/cmdline", pid);
			read_cmdline(filename, proc);

			sprintf(filename, "/proc/%d/status", pid);
			read_status(filename, proc);

			read_policy(pid, proc);

			proc->num_threads = 0;
		} else {
			sprintf(filename, "/proc/%d/cmdline", pid);
			read_cmdline(filename, &cur_proc);

			sprintf(filename, "/proc/%d/status", pid);
			read_status(filename, &cur_proc);

			proc = NULL;
		}

		sprintf(filename, "/proc/%d/task", pid);
		task_dir = opendir(filename);
		if (!task_dir) continue;

		while ((tid_dir = readdir(task_dir))) {
			if (!isdigit(tid_dir->d_name[0]))
				continue;

			if (threads) {
				tid = atoi(tid_dir->d_name);

				proc = alloc_proc();

				proc->pid = pid; proc->tid = tid;

				sprintf(filename, "/proc/%d/task/%d/stat", pid, tid);
				read_stat(filename, proc);

				read_policy(tid, proc);

				strcpy(proc->name, cur_proc.name);
				proc->uid = cur_proc.uid;
				proc->gid = cur_proc.gid;

				add_proc(proc_num++, proc);
			} else {
				proc->num_threads++;
			}
		}

		closedir(task_dir);

		if (!threads)
			add_proc(proc_num++, proc);
	}

	for (i = proc_num; i < num_new_procs; i++)
		new_procs[i] = NULL;

	closedir(proc_dir);
}

static int read_stat(char *filename, struct proc_info *proc) {
	FILE *file;
	char buf[MAX_LINE], *open_paren, *close_paren;

	file = fopen(filename, "r");
	if (!file) return 1;
	fgets(buf, MAX_LINE, file);
	fclose(file);

	/* Split at first '(' and last ')' to get process name. */
	open_paren = strchr(buf, '(');
	close_paren = strrchr(buf, ')');
	if (!open_paren || !close_paren) return 1;

	*open_paren = *close_paren = '\0';
	strncpy(proc->tname, open_paren + 1, THREAD_NAME_LEN);
	proc->tname[THREAD_NAME_LEN-1] = 0;

	/* Scan rest of string. */
	sscanf(close_paren + 1,
		   " %c " "%*d %*d %*d %*d %*d %*d %*d %*d %*d %*d "
		   "%" SCNu64
		   "%" SCNu64 "%*d %*d %*d %*d %*d %*d %*d "
		   "%" SCNu64
		   "%" SCNu64 "%*d %*d %*d %*d %*d %*d %*d %*d %*d %*d %*d %*d %*d %*d "
		   "%d",
		   &proc->state,
		   &proc->utime,
		   &proc->stime,
		   &proc->vss,
		   &proc->rss,
		   &proc->prs);

	return 0;
}

static void add_proc(int proc_num, struct proc_info *proc) {
	int i;

	if (proc_num >= num_new_procs) {
		new_procs = realloc(new_procs, 2 * num_new_procs * sizeof(struct proc_info *));
		if (!new_procs) die("Could not expand procs array.\n");
		for (i = num_new_procs; i < 2 * num_new_procs; i++)
			new_procs[i] = NULL;
		num_new_procs = 2 * num_new_procs;
	}
	new_procs[proc_num] = proc;
}

static int read_cmdline(char *filename, struct proc_info *proc) {
	FILE *file;
	char line[MAX_LINE];

	line[0] = '\0';
	file = fopen(filename, "r");
	if (!file) return 1;
	fgets(line, MAX_LINE, file);
	fclose(file);
	if (strlen(line) > 0) {
		strncpy(proc->name, line, PROC_NAME_LEN);
		proc->name[PROC_NAME_LEN-1] = 0;
	} else
		proc->name[0] = 0;
	return 0;
}

static void read_policy(int pid, struct proc_info *proc) {
	SchedPolicy p;
	if (get_sched_policy(pid, &p) < 0)
		strlcpy(proc->policy, "unk", POLICY_NAME_LEN);
	else {
		strlcpy(proc->policy, get_sched_policy_name(p), POLICY_NAME_LEN);
		proc->policy[2] = '\0';
	}
}

static int read_status(char *filename, struct proc_info *proc) {
	FILE *file;
	char line[MAX_LINE];
	unsigned int uid, gid;

	file = fopen(filename, "r");
	if (!file) return 1;
	while (fgets(line, MAX_LINE, file)) {
		sscanf(line, "Uid: %u", &uid);
		sscanf(line, "Gid: %u", &gid);
	}
	fclose(file);
	proc->uid = uid; proc->gid = gid;
	return 0;
}

/*static */struct proc_info *find_old_proc(pid_t pid, pid_t tid) {
	int i;

	for (i = 0; i < num_old_procs; i++)
		if (old_procs[i] && (old_procs[i]->pid == pid) && (old_procs[i]->tid == tid))
			return old_procs[i];

	return NULL;
}

/*static */void free_old_procs(void) {
	int i;

	for (i = 0; i < num_old_procs; i++)
		if (old_procs[i])
			free_proc(old_procs[i]);

	free(old_procs);
}

static int proc_cpu_cmp(const void *a, const void *b) {
	struct proc_info *pa, *pb;

	pa = *((struct proc_info **)a); pb = *((struct proc_info **)b);

	if (!pa && !pb) return 0;
	if (!pa) return 1;
	if (!pb) return -1;

	return -numcmp(pa->delta_time, pb->delta_time);
}

static int proc_vss_cmp(const void *a, const void *b) {
	struct proc_info *pa, *pb;

	pa = *((struct proc_info **)a); pb = *((struct proc_info **)b);

	if (!pa && !pb) return 0;
	if (!pa) return 1;
	if (!pb) return -1;

	return -numcmp(pa->vss, pb->vss);
}

static int proc_rss_cmp(const void *a, const void *b) {
	struct proc_info *pa, *pb;

	pa = *((struct proc_info **)a); pb = *((struct proc_info **)b);

	if (!pa && !pb) return 0;
	if (!pa) return 1;
	if (!pb) return -1;

	return -numcmp(pa->rss, pb->rss);
}

static int proc_thr_cmp(const void *a, const void *b) {
	struct proc_info *pa, *pb;

	pa = *((struct proc_info **)a); pb = *((struct proc_info **)b);

	if (!pa && !pb) return 0;
	if (!pa) return 1;
	if (!pb) return -1;

	return -numcmp(pa->num_threads, pb->num_threads);
}

static int numcmp(long long a, long long b) {
	if (a < b) return -1;
	if (a > b) return 1;
	return 0;
}

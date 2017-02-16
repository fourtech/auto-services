#ifndef _PROCESSES_MONITOR_H
#define _PROCESSES_MONITOR_H

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

#include <cutils/sched_policy.h>

struct cpu_info {
	long unsigned utime, ntime, stime, itime;
	long unsigned iowtime, irqtime, sirqtime;
};

#define PROC_NAME_LEN 64
#define THREAD_NAME_LEN 32
#define POLICY_NAME_LEN 4

struct proc_info {
	struct proc_info *next;
	pid_t pid;
	pid_t tid;
	uid_t uid;
	gid_t gid;
	char name[PROC_NAME_LEN];
	char tname[THREAD_NAME_LEN];
	char state;
	uint64_t utime;
	uint64_t stime;
	uint64_t delta_utime;
	uint64_t delta_stime;
	uint64_t delta_time;
	uint64_t vss;
	uint64_t rss;
	int prs;
	int num_threads;
	char policy[POLICY_NAME_LEN];
};

struct proc_list {
	struct proc_info **array;
	int size;
};

#define die(...) { fprintf(stderr, __VA_ARGS__); exit(EXIT_FAILURE); }

#define INIT_PROCS 50
#define THREAD_MULT 8
static struct proc_info **old_procs, **new_procs;
static int num_old_procs, num_new_procs;
static struct proc_info *free_procs;
static int num_used_procs, num_free_procs;

static int max_procs, delay, iterations, threads;

static struct cpu_info old_cpu, new_cpu;

void init_procs(void);
int read_procs_out(struct proc_info **old_procs_out, struct proc_info **new_procs_out);
struct proc_info *find_old_proc(pid_t pid, pid_t tid);
void free_old_procs(void);

static struct proc_info *alloc_proc(void);
static void free_proc(struct proc_info *proc);
static void read_procs(void);
static int read_stat(char *filename, struct proc_info *proc);
static void read_policy(int pid, struct proc_info *proc);
static void add_proc(int proc_num, struct proc_info *proc);
static int read_cmdline(char *filename, struct proc_info *proc);
static int read_status(char *filename, struct proc_info *proc);
static int (*proc_cmp)(const void *a, const void *b);
static int proc_cpu_cmp(const void *a, const void *b);
static int proc_vss_cmp(const void *a, const void *b);
static int proc_rss_cmp(const void *a, const void *b);
static int proc_thr_cmp(const void *a, const void *b);
static int numcmp(long long a, long long b);
#endif // _PROCESSES_MONITOR_H

#include <rte_meter.h>
#include <rte_red.h>
#include <math.h>
#include <rte_log.h>
#include <rte_common.h>
#include <inttypes.h>
#include "qos.h"

#ifndef RTE_METER_TB_PERIOD_MIN
#define RTE_METER_TB_PERIOD_MIN      100
#endif

#ifndef APP_METER_COLORS
#define APP_METER_COLORS             3
#endif

// app meter configurations, using srtcm
struct rte_meter_srtcm app_meter_srtcm[APP_FLOWS_MAX];

struct rte_meter_srtcm_params app_srtcm_params[] = {
    {.cir = 1000000 * 160, .cbs = 80000, .ebs = 80000},
    {.cir = 1000000 * 80, .cbs = 70000, .ebs = 10000},
    {.cir = 1000000 * 40, .cbs = 30000, .ebs = 10000},
    {.cir = 1000000 * 20, .cbs = 15000, .ebs = 5000}
};

// app red parameters
struct rte_red_params app_red_params[APP_FLOWS_MAX][APP_METER_COLORS] = {
    {
        {.min_th = 1000, .max_th = 1020, .maxp_inv = 250, .wq_log2 = 10},
        {.min_th = 1000, .max_th = 1020, .maxp_inv = 250, .wq_log2 = 10},
        {.min_th = 0, .max_th = 250, .maxp_inv = 100, .wq_log2 = 4}
    },
    {
        {.min_th = 1000, .max_th = 1020, .maxp_inv = 250, .wq_log2 = 9},
        {.min_th = 900, .max_th = 1020, .maxp_inv = 10, .wq_log2 = 4},
        {.min_th = 0, .max_th = 1, .maxp_inv = 1, .wq_log2 = 1}
    },
    {
        {.min_th = 1000, .max_th = 1020, .maxp_inv = 250, .wq_log2 = 9},
        {.min_th = 900, .max_th = 1020, .maxp_inv = 10, .wq_log2 = 4},
        {.min_th = 0, .max_th = 1, .maxp_inv = 1, .wq_log2 = 1}
    },
    {
        {.min_th = 1000, .max_th = 1020, .maxp_inv = 250, .wq_log2 = 9},
        {.min_th = 900, .max_th = 1020, .maxp_inv = 10, .wq_log2 = 4},
        {.min_th = 0, .max_th = 1, .maxp_inv = 1, .wq_log2 = 1}
    }
};

// app red configuration
struct rte_red_config app_red_config[APP_FLOWS_MAX][APP_METER_COLORS];

// app runtime data structure
struct rte_red app_red[APP_FLOWS_MAX][APP_METER_COLORS];

// queue for each flow
unsigned q[APP_FLOWS_MAX][APP_METER_COLORS] = { {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}};


// last burst period 
uint64_t last_period = 0;


static void
rte_meter_get_tb_params(uint64_t hz, uint64_t rate, uint64_t *tb_period, uint64_t *tb_bytes_per_period)
{
	double period = ((double) hz) / ((double) rate);

	if (period >= RTE_METER_TB_PERIOD_MIN) {
		*tb_bytes_per_period = 1;
		*tb_period = (uint64_t) period;
	} else {
		*tb_bytes_per_period = (uint64_t) ceil(RTE_METER_TB_PERIOD_MIN / period);
		*tb_period = (hz * (*tb_bytes_per_period)) / rate;
	}
}

/**
 * This function will be called only once at the beginning of the test. 
 * You can initialize your meter here.
 * 
 * int rte_meter_srtcm_config(struct rte_meter_srtcm *m, struct rte_meter_srtcm_params *params);
 * @return: 0 upon success, error code otherwise
 * 
 * void rte_exit(int exit_code, const char *format, ...)
 * #define rte_panic(...) rte_panic_(__func__, __VA_ARGS__, "dummy")
 * 
 * uint64_t rte_get_tsc_hz(void)
 * @return: The frequency of the RDTSC timer resolution
 * 
 * static inline uint64_t rte_get_tsc_cycles(void)
 * @return: The time base for this lcore.
 */
int
qos_meter_init(void)
{
    uint64_t hz[] = {1000000, 1000000, 1000000, 1000000};
    for (uint32_t i = 0; i < APP_FLOWS_MAX; i++) {
        app_meter_srtcm[i].tc = app_meter_srtcm [i].cbs = app_srtcm_params[i].cbs;
        app_meter_srtcm[i].te = app_meter_srtcm [i].ebs = app_srtcm_params[i].ebs;
        app_meter_srtcm[i].time = 0;
    
        rte_meter_get_tb_params(hz[i], app_srtcm_params[i].cir, 
            &app_meter_srtcm[i].cir_period, &app_meter_srtcm[i].cir_bytes_per_period);
        
        RTE_LOG(INFO, METER, "Low level srTCM config: \n"
            "\tCIR period = %" PRIu64 ", CIR bytes per period = %" PRIu64 "\n",
            app_meter_srtcm[i].cir_period, app_meter_srtcm[i].cir_bytes_per_period);
    }
    return 0;
}

/**
 * This function will be called for every packet in the test, 
 * after which the packet is marked by returning the corresponding color.
 * 
 * A packet is marked green if it doesn't exceed the CBS, 
 * yellow if it does exceed the CBS, but not the EBS, and red otherwise
 * 
 * The pkt_len is in bytes, the time is in nanoseconds.
 * 
 * Point: We need to convert ns to cpu circles
 * Point: Time is not counted from 0
 * 
 * static inline enum rte_meter_color rte_meter_srtcm_color_blind_check(struct rte_meter_srtcm *m,
	uint64_t time, uint32_t pkt_len)
 * 
 * enum qos_color { GREEN = 0, YELLOW, RED };
 * enum rte_meter_color { e_RTE_METER_GREEN = 0, e_RTE_METER_YELLOW,  
	e_RTE_METER_RED, e_RTE_METER_COLORS };
 */ 

enum qos_color
qos_meter_run(uint32_t flow_id, uint32_t pkt_len, uint64_t time)
{   
    enum rte_meter_color output_color = rte_meter_srtcm_color_blind_check(&app_meter_srtcm[flow_id], time, pkt_len);
    if (output_color == e_RTE_METER_GREEN)
        return GREEN;
    else if (output_color == e_RTE_METER_YELLOW)
        return YELLOW;
    else 
        return RED;
}


/**
 * This function will be called only once at the beginning of the test. 
 * You can initialize you dropper here
 * 
 * int rte_red_rt_data_init(struct rte_red *red);
 * @return Operation status, 0 success
 * 
 * int rte_red_config_init(struct rte_red_config *red_cfg, const uint16_t wq_log2, 
   const uint16_t min_th, const uint16_t max_th, const uint16_t maxp_inv);
 * @return Operation status, 0 success 
 */
int
qos_dropper_init(void)
{   
    int ret;
    // Init red runtime data
    for (int i = 0; i < APP_FLOWS_MAX; i++) {
        for (int j = 0; j < APP_METER_COLORS; j++) {
            ret = rte_red_rt_data_init(&app_red[i][j]);
            if(ret) {
                rte_panic("fid: %d, color: %d, call rte_red_rt_data_init failed\n", i, j);
                return ret;
            }
        }
    }
     
    // Init red cfg struct
    for (int i = 0; i < APP_FLOWS_MAX; i++) {
        for (int j = 0; j < APP_METER_COLORS; j++) {
            ret = rte_red_config_init(&app_red_config[i][j],
                app_red_params[i][j].wq_log2,
                app_red_params[i][j].min_th,
                app_red_params[i][j].max_th,
                app_red_params[i][j].maxp_inv);
            if (ret) {
                rte_panic("fid: %d, color: %d, call rte_red_config_init failed\n", i, j);
                return ret;
            }
        }
    }
    
    return 0;
}

/**
 * This function will be called for every tested packet after being marked by the meter, 
 * and will make the decision whether to drop the packet by returning the decision (0 pass, 1 drop)
 * 
 * The probability of drop increases as the estimated average queue size grows
 * 
 * static inline void rte_red_mark_queue_empty(struct rte_red *red, const uint64_t time)
 * @brief Callback to records time that queue became empty
 * @param q_time : Start of the queue idle time (q_time) 
 * 
 * static inline int rte_red_enqueue(const struct rte_red_config *red_cfg,
	struct rte_red *red, const unsigned q, const uint64_t time)
 * @param q [in] updated queue size in packets   
 * @return Operation status
 * @retval 0 enqueue the packet
 * @retval 1 drop the packet based on max threshold criteria
 * @retval 2 drop the packet based on mark probability criteria
 */

int
qos_dropper_run(uint32_t flow_id, enum qos_color color, uint64_t time)
{
    // clear queue every burst period
    if (time != last_period) {
        last_period = time;
        q[flow_id][color] = 0;
        rte_red_mark_queue_empty(&app_red[flow_id][color], time);
    }
    
    int ret;
    ret = rte_red_enqueue(&app_red_config[flow_id][color], 
                            &app_red[flow_id][color],
                            q[flow_id][color],
                            time);
    if (!ret)
        q[flow_id][color]++;
    
    return ret;
}

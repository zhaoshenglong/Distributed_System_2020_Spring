
#ifndef _HELPER_H_
#define _HELPER_H_

#include "rdt_struct.h"

#define MAX_SEQ         15
#define WINDOW_SZ       8

/* Header size for packet */
#define HEADERSIZE      6

#define TIMEOUT         0.3

typedef int seq_nr;


/* virtual timer for sender */
struct virtual_timer {
    double start_time;
    seq_nr seq;
};


/*
 * returns true if b is between a and c circularly
 * 
 */
bool between(seq_nr a, seq_nr b, seq_nr c);

/**
 * returns 16-bit internet checksum
 */
unsigned short compute_checksum(struct packet*);

/**
 * returns true if checksum valid
 * If we only compute checksum for full packet, corruption
 * will remain possible. Consequently, I add some check for
 * packet.
 */
bool verify_checksum(struct packet*);

/* increment sequence number circularly */
void inc(seq_nr *n);


/* Get & Set methods */
void set_pkt_payload_size(struct packet *, char size);

void set_pkt_end(struct packet *, char is_end);

void set_pkt_ack(struct packet *, char ack);

void set_pkt_seq(struct packet *, char seq);

void set_pkt_checksum(struct packet *, unsigned short checksum);

int get_pkt_payload_size(struct packet *);

bool is_pkt_end(struct packet *);

seq_nr get_pkt_ack(struct packet *);

seq_nr get_pkt_seq(struct packet *);

/* For debug */
void print_pkt(struct packet *);
#endif
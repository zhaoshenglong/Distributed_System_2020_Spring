#include "helper.h"
#include <stdio.h>

bool between(seq_nr a, seq_nr b, seq_nr c) {
    if ((a < b && b <= c) || (a > c && b <= c) || (a > c && b > a)) 
        return true;
    else
        return false;
}

unsigned short compute_checksum(struct packet *pkt) {

    unsigned short *p = (unsigned short *)pkt;
    unsigned int res = 0;
    unsigned short *end = (unsigned short *)((char*)p + sizeof(struct packet));
    while (p != end) {
        unsigned short r = *p;
        res += (unsigned)r;
        p++;
    }
    while(res >= (1 << 16)) {
        res = (res & 0xffff) + ((res >> 16) & 0xffff); 
    }
    return ~res;
}

bool verify_checksum(struct packet *pkt) {
    unsigned int res = 0;
    unsigned short *p = (unsigned short *)pkt;
    unsigned short *end = (unsigned short *)((char *)p + sizeof(struct packet));
    while (p != end) {
        unsigned short r = *p;
        res += (unsigned)r;
        p++;
    }
    while (res >= (1 << 16)) {
        res = (res & 0xffff) + ((res >> 16) & 0xffff); 
    }
    // printf("%x\n", res);

    if(get_pkt_ack(pkt) > MAX_SEQ || get_pkt_ack(pkt) < 0) 
        return false;
    if(get_pkt_seq(pkt) > MAX_SEQ || get_pkt_seq(pkt) < 0)
        return false;
    for (int i = 6; i < RDT_PKTSIZE; i++) {
        if(pkt->data[i] < 0) {
            return false;
        }
    }
    return res == 0xffff;
}

void inc(seq_nr *n) {
    if (*n < MAX_SEQ) {
        (*n)++;
    } else *n = 0;
}

void set_pkt_payload_size(struct packet *pkt, char size){
    pkt->data[0] = size;
}

void set_pkt_end(struct packet *pkt, char is_end) {
    pkt->data[1] = is_end;
}

void set_pkt_ack(struct packet *pkt, char ack) {
    pkt->data[2] = ack;
}

void set_pkt_seq(struct packet *pkt, char seq) {
    pkt->data[3] = seq;
}

void set_pkt_checksum(struct packet *pkt, unsigned short checksum) {
    *(unsigned short*)(pkt->data + 4) = checksum;
}

int get_pkt_payload_size(struct packet * pkt) {
    return pkt->data[0];
}

bool is_pkt_end(struct packet *pkt) {
    return pkt->data[1] == 1;
}

seq_nr get_pkt_ack(struct packet *pkt) {
    return pkt->data[2];
}

seq_nr get_pkt_seq(struct packet *pkt) {
    return pkt->data[3];
}


void print_pkt(struct packet *p) {
    printf("^^^^^^^^^^^^^^^^^Start print pkt - size: %d, end: %d, ack: %d, seq: %d, checksum: %x^^^^^^^^^^^^^^^^^^^^\n", get_pkt_payload_size(p), p->data[1], get_pkt_ack(p), get_pkt_seq(p), *(unsigned short*)(p->data + 4));
    printf("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^        Start print payload        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^\n");
    for (int i = 0; i < 12; i++) {
        for (int j = 0; j < 10; j++) {
            printf("%.2x ",p->data[i*10+j + 6]);
        }
        printf("\n");
    }
    printf("%.2x %.2x\n", p->data[126], p->data[127]);

}